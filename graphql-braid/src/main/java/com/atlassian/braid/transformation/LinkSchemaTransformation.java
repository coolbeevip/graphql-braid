package com.atlassian.braid.transformation;

import static com.atlassian.braid.LinkArgument.ArgumentSource.FIELD_ARGUMENT;
import static com.atlassian.braid.LinkArgument.ArgumentSource.OBJECT_FIELD;
import static com.atlassian.braid.TypeUtils.findMutationType;
import static com.atlassian.braid.TypeUtils.findQueryType;
import static com.atlassian.braid.transformation.DataFetcherUtils.getLinkDataLoaderKey;
import static java.lang.String.format;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import com.atlassian.braid.BatchLoaderEnvironment;
import com.atlassian.braid.Link;
import com.atlassian.braid.LinkArgument;
import com.atlassian.braid.SchemaNamespace;
import com.atlassian.braid.SchemaSource;
import com.atlassian.braid.TypeUtils;
import graphql.execution.DataFetcherResult;
import graphql.language.FieldDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.ListType;
import graphql.language.NonNullType;
import graphql.language.ObjectTypeDefinition;
import graphql.language.Type;
import graphql.language.TypeDefinition;
import graphql.language.TypeName;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.idl.TypeDefinitionRegistry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.dataloader.BatchLoader;
import org.dataloader.DataLoader;

/**
 * A {@link SchemaTransformation} for processing links, which add fields to source object types. The field to add is
 * specified by the {@link Link}. The link field values
 * are fetched from top-level fields of the target schema sources.
 */
public class LinkSchemaTransformation implements SchemaTransformation {
    @Override
    public Map<String, BatchLoader> transform(BraidingContext braidingContext) {
        final Map<SchemaNamespace, BraidSchemaSource> sources = braidingContext.getDataSources();
        final ObjectTypeDefinition queryObjectTypeDefinition = braidingContext.getQueryObjectTypeDefinition();
        final ObjectTypeDefinition mutationObjectTypeDefinition = braidingContext.getMutationObjectTypeDefinition();
        final BatchLoaderEnvironment batchLoaderEnvironment = braidingContext.getBatchLoaderEnvironment();
        final TypeDefinitionRegistry typeDefinitionRegistry = braidingContext.getRegistry();

        Map<String, BatchLoader> batchLoaders = new HashMap<>();
        for (BraidSchemaSource source : sources.values()) {
            TypeDefinitionRegistry typeRegistry = source.getTypeRegistry();
            SchemaSource sourceSchemaSource = source.getSchemaSource();
            final TypeDefinitionRegistry privateTypes = sourceSchemaSource.getPrivateSchema();

            Map<String, TypeDefinition> dsTypes = new HashMap<>(typeDefinitionRegistry.types());

            for (Link link : sourceSchemaSource.getLinks()) {

                ObjectTypeDefinition braidObjectTypeDefinition = getObjectTypeDefinition(
                        queryObjectTypeDefinition,
                        mutationObjectTypeDefinition,
                        typeDefinitionRegistry,
                        dsTypes,
                        source.getLinkBraidSourceType(link)
                );

                // TopLevelFields are not yet in braidTypeRegistry
                // they will be copied in the TopLevelFieldTransformation that runs next
                if (braidObjectTypeDefinition.equals(TypeUtils.findQueryType(typeDefinitionRegistry).orElse(null))) {
                    braidObjectTypeDefinition = TypeUtils.findQueryType(typeRegistry).get();
                }
                if (braidObjectTypeDefinition.equals(TypeUtils.findMutationType(typeDefinitionRegistry).orElse(null))) {
                    braidObjectTypeDefinition = TypeUtils.findMutationType(typeRegistry).get();
                }

                validateSourceFromFieldExists(source, link, privateTypes);

                Optional<FieldDefinition> newField = braidObjectTypeDefinition.getFieldDefinitions().stream()
                        .filter(d -> d.getName().equals(link.getNewFieldName()))
                        .findFirst();

                BraidSchemaSource targetSource = sources.get(link.getTargetNamespace());
                if (targetSource == null) {
                    throw new IllegalArgumentException("Can't find target schema source: " + link.getTargetNamespace());
                }
                if (!targetSource.hasType(link.getTargetType())) {
                    throw new IllegalArgumentException("Can't find target type: " + link.getTargetType());
                }

                FieldDefinition topLevelField = topLevelFieldForLink(link, targetSource);
                if (!link.isNoSchemaChangeNeeded()) {
                    modifySchema(link, braidObjectTypeDefinition, newField, topLevelField);
                }

                String type = source.getLinkBraidSourceType(link);
                String field = link.getNewFieldName();
                String linkDataLoaderKey = getLinkDataLoaderKey(type, field);

                // Create the coordinating DataFetcher that uses the BatchLoader registered below to load data.
                DataFetcher<CompletableFuture<DataFetcherResult<Object>>> dataFetcher = env -> {
                    DataLoader<DataFetchingEnvironment, DataFetcherResult<Object>> dataLoader = env.getDataLoader(linkDataLoaderKey);
                    return dataLoader.load(env);
                };
                braidingContext.registerDataFetcher(type, field, dataFetcher);

                // Create the BatchLoader using the target SchemaSource. This BatchLoader is used by the DataFetcher
                // created above during execution.
                SchemaSource targetSchemaSource = targetSource.getSchemaSource();
                BatchLoader<DataFetchingEnvironment, DataFetcherResult<Object>> batchLoader =
                        targetSchemaSource.newBatchLoader(
                                targetSchemaSource,
                                new LinkTransformation(link),
                                batchLoaderEnvironment
                        );

                batchLoaders.put(linkDataLoaderKey, batchLoader);
            }
        }
        return batchLoaders;
    }

    private static FieldDefinition topLevelFieldForLink(Link link, BraidSchemaSource targetSource) {
        return TypeUtils.findQueryType(targetSource.getSchemaSource().getPrivateSchema())
                .flatMap(queryType -> queryType.getFieldDefinitions().stream()
                        .filter(fieldDefinitiuon -> link.getTopLevelQueryField().equals(fieldDefinitiuon.getName()))
                        .findFirst())
                .orElseThrow(() -> new IllegalStateException(format("Cannot find top level query field '%s' in source '%s' for link on field '%s' defined in '%s'",
                        link.getTopLevelQueryField(), link.getTargetNamespace(), link.getNewFieldName(), link.getSourceNamespace())));
    }

//    private static ObjectTypeDefinition modifySchema(Link link, ObjectTypeDefinition typeDefinition, Optional<FieldDefinition> newField,
//                                     FieldDefinition topLevelField) {
//
//        ObjectTypeDefinition newDefinition = typeDefinition;
//        List<FieldDefinition> fieldDefs = newDefinition.getFieldDefinitions();
//
//        Map<String, FieldDefinition> objectFields = typeDefinition.getFieldDefinitions()
//                .stream()
//                .filter(Objects::nonNull)
//                .collect(toMap(FieldDefinition::getName, identity()));
//
//
//        link.getLinkArguments().stream()
//                .filter(linkArgument -> linkArgument.getArgumentSource() == OBJECT_FIELD
//                        && linkArgument.isRemoveInputField())
//                .map(LinkArgument::getSourceName)
//                .forEach(fieldToRemove -> {
//                    Optional.ofNullable(objectFields.get(fieldToRemove))
//                            .ifPresent(fieldDef -> fieldDefs.remove(fieldDef));
//                });
//
//        final Type targetType = new TypeName(link.getTargetType());
//        if (!newField.isPresent()) {
//          FieldDefinition field = new FieldDefinition(link.getNewFieldName(),
//            adjustTypeForSimpleLink(link, objectFields, targetType));
//
//          List<InputValueDefinition> inputValueDefs = link.getLinkArguments().stream()
//            .filter(linkArgument -> linkArgument.getArgumentSource() == FIELD_ARGUMENT)
//            .flatMap(linkArgument -> buildInputValueDefinitionForLink(topLevelField, linkArgument))
//            .collect(Collectors.toList());
//          field = field.transform(b -> b.inputValueDefinitions(inputValueDefs));
//          fieldDefs.add(field);
//        } else if (isListType(newField.get().getType())) {
//          fieldDefs.remove(newField.get());
//            if (newField.get().getType() instanceof NonNullType) {
//              fieldDefs.add(newField.get().transform(b-> b.type(new NonNullType(new ListType(targetType)))));
//            } else {
//              fieldDefs.add(newField.get().transform(b -> b.type(new ListType(targetType))));
//            }
//        } else {
//          fieldDefs.remove(newField.get());
//          fieldDefs.add(newField.get().transform(b -> b.type(targetType)));
//        }
//        return newDefinition.transform(b -> b.fieldDefinitions(fieldDefs));
//    }

  private static void modifySchema(Link link, ObjectTypeDefinition typeDefinition, Optional<FieldDefinition> newField,
    FieldDefinition topLevelField) {
    Map<String, FieldDefinition> objectFields = typeDefinition.getFieldDefinitions()
      .stream()
      .filter(Objects::nonNull)
      .collect(toMap(FieldDefinition::getName, identity()));


    link.getLinkArguments().stream()
      .filter(linkArgument -> linkArgument.getArgumentSource() == OBJECT_FIELD
        && linkArgument.isRemoveInputField())
      .map(LinkArgument::getSourceName)
      .forEach(fieldToRemove -> {
        Optional.ofNullable(objectFields.get(fieldToRemove))
          .ifPresent(fieldDef -> typeDefinition.getFieldDefinitions().remove(fieldDef));
      });

    Type targetType = new TypeName(link.getTargetType());
    targetType = link.targetNonNullable() ? NonNullType.newNonNullType(targetType).build() : targetType;

    if (!newField.isPresent()) {
      targetType = adjustTypeForSimpleLink(link, objectFields, targetType);

      FieldDefinition field = new FieldDefinition(link.getNewFieldName(), targetType);

      List<InputValueDefinition> inputValueDefs = link.getLinkArguments().stream()
        .filter(linkArgument -> linkArgument.getArgumentSource() == FIELD_ARGUMENT)
        .flatMap(linkArgument -> buildInputValueDefinitionForLink(topLevelField, linkArgument))
        .collect(Collectors.toList());
      field.getInputValueDefinitions().addAll(inputValueDefs);
      typeDefinition.getFieldDefinitions().add(field);
    } else if (isListType(newField.get().getType())) {
      if (newField.get().getType() instanceof NonNullType) {
        newField.get().setType(new NonNullType(new ListType(targetType)));
      } else {
        newField.get().setType(new ListType(targetType));
      }
    } else {
      // Change source field type to the braided type
      newField.get().setType(targetType);
    }
  }

    private static Stream<InputValueDefinition> buildInputValueDefinitionForLink(FieldDefinition topLevelField, LinkArgument linkArgument) {
        return topLevelField.getInputValueDefinitions().stream()
                .filter(input -> linkArgument.getQueryArgumentName().equals(input.getName()))
                .findFirst()
                .map(input -> Stream.of(InputValueDefinition.newInputValueDefinition()
                        .name(linkArgument.getSourceName())
                        .type(input.getType())
                        .build()))
                .orElse(Stream.empty());
    }

    private static Type adjustTypeForSimpleLink(Link link, Map<String, FieldDefinition> objectFields, Type targetType) {
        if (link.isSimpleLink()) {
            Optional<FieldDefinition> sourceInputField = Optional.ofNullable(objectFields.get(link.getSourceInputFieldName()));
            // Add source field to schema if not already there
            if (sourceInputField.isPresent() && isListType(sourceInputField.get().getType())) {
                targetType = new ListType(targetType);
            }
        }
        return targetType;
    }

    private static ObjectTypeDefinition getObjectTypeDefinition(ObjectTypeDefinition queryObjectTypeDefinition,
                                                                ObjectTypeDefinition mutationObjectTypeDefinition,
                                                                TypeDefinitionRegistry typeRegistry,
                                                                Map<String, TypeDefinition> dsTypes,
                                                                String linkSourceType) {
        ObjectTypeDefinition typeDefinition = (ObjectTypeDefinition) dsTypes.get(linkSourceType);
        if (typeDefinition == null && linkSourceType.equals(queryObjectTypeDefinition.getName())) {
            typeDefinition = findQueryType(typeRegistry).orElse(null);
            if (typeDefinition == null && linkSourceType.equals(mutationObjectTypeDefinition.getName())) {
                typeDefinition = findMutationType(typeRegistry).orElse(null);
            }
        }

        if (typeDefinition == null) {
            throw new IllegalArgumentException("Can't find source type: " + linkSourceType);
        }
        return typeDefinition;
    }

    private static void validateSourceFromFieldExists(BraidSchemaSource source, Link link,
                                                      TypeDefinitionRegistry privateTypeDefinitionRegistry) {
        final String sourceType = source.getSourceTypeName(link.getSourceType());
        ObjectTypeDefinition typeDefinition = privateTypeDefinitionRegistry
                .getType(sourceType, ObjectTypeDefinition.class)
                .orElseThrow(() -> new IllegalArgumentException(
                        format("Can't find source type '%s' in private schema for link %s",
                                sourceType, link.getNewFieldName())));
        Map<String, FieldDefinition> fieldsByName = typeDefinition.getFieldDefinitions().stream()
                .collect(toMap(FieldDefinition::getName, identity()));

        List<String> missingSourceObjectFields = link.getLinkArguments().stream()
                .filter(linkArgument -> linkArgument.getArgumentSource() == LinkArgument.ArgumentSource.OBJECT_FIELD)
                .filter(linkArgument -> !fieldsByName.containsKey(linkArgument.getSourceName()))
                .map(LinkArgument::getSourceName)
                .collect(Collectors.toList());


        if (!missingSourceObjectFields.isEmpty()) {
            String missingFieldsStr = missingSourceObjectFields.stream().collect(Collectors.joining(", "));
            throw new IllegalArgumentException("Can't find source from field: " + missingFieldsStr);
        }
    }

    private static boolean isListType(Type type) {
        return type instanceof ListType ||
                (type instanceof NonNullType && ((NonNullType) type).getType() instanceof ListType);
    }
}
