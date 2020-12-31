package com.atlassian.braid.transformation;

import com.atlassian.braid.BatchLoaderEnvironment;
import com.atlassian.braid.FieldRename;
import com.atlassian.braid.SchemaNamespace;
import graphql.execution.DataFetcherResult;
import graphql.language.FieldDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.Type;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import org.dataloader.BatchLoader;
import org.dataloader.DataLoader;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.atlassian.braid.TypeUtils.unwrap;
import static com.atlassian.braid.transformation.DataFetcherUtils.getDataLoaderKey;
import static com.atlassian.braid.transformation.DataFetcherUtils.getLinkDataLoaderKey;
import static graphql.schema.DataFetchingEnvironmentBuilder.newDataFetchingEnvironment;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

/**
 * A {@link SchemaTransformation} for processing top-level fields for the root operation type.
 */
public class TopLevelSchemaTransformation implements SchemaTransformation {

    /**
     * It scans all the <code>SchemaSource</code>s. It adds top-level field definitions, their <code>DataFetcher</code>s
     * and type definitions to the <code>BraidingContext</code>. It also collects <code>BatchLoader</code>s used by
     * the <code>DataFetcher</code>s.
     */
    @Override
    public Map<String, BatchLoader> transform(BraidingContext braidingContext) {
        Map<SchemaNamespace, BraidSchemaSource> dataSources = braidingContext.getDataSources();

        // Add top-level fields and create the BatchLoaders.
        List<FieldDataLoaderRegistration> queryLoaders = addSchemaSourcesTopLevelFieldsToOperation(
                dataSources,
                braidingContext.getQueryObjectTypeDefinition(),
                BraidSchemaSource::getQueryType,
                BraidSchemaSource::getQueryFieldRenames,
                braidingContext.getBatchLoaderEnvironment());
        List<FieldDataLoaderRegistration> mutationLoaders = addSchemaSourcesTopLevelFieldsToOperation(
                dataSources,
                braidingContext.getMutationObjectTypeDefinition(),
                BraidSchemaSource::getMutationType,
                BraidSchemaSource::getMutationFieldRenames,
                braidingContext.getBatchLoaderEnvironment());

        return Stream.concat(queryLoaders.stream(), mutationLoaders.stream())
                .peek(reg -> registerDataFetcher(braidingContext, reg.type, reg.field))
                .collect(toMap(reg -> getDataLoaderKey(reg.type, reg.field),
                               reg -> reg.loader));
    }

    private static void registerDataFetcher(BraidingContext context, String type, String field) {
        TopLevelDataFetcher dataFetcher = new TopLevelDataFetcher(type, field);
        context.registerDataFetcher(type, field, dataFetcher);
    }

    private static List<FieldDataLoaderRegistration> addSchemaSourcesTopLevelFieldsToOperation
            (Map<SchemaNamespace, BraidSchemaSource> dataSources,
             ObjectTypeDefinition braidOperationType,
             Function<BraidSchemaSource, Optional<ObjectTypeDefinition>> findOperationType,
             BiFunction<BraidSchemaSource, String, Optional<FieldRename>> getFieldRename,
             BatchLoaderEnvironment batchLoaderEnvironment) {
        return dataSources.values()
                .stream()
                .map(source ->
                        addSchemaSourceTopLevelFieldsToOperation(
                                source,
                                braidOperationType,
                                findOperationType,
                                getFieldRename,
                                batchLoaderEnvironment))
                .flatMap(Collection::stream)
                .collect(toList());
    }

    private static List<FieldDataLoaderRegistration> addSchemaSourceTopLevelFieldsToOperation(
            BraidSchemaSource source,
            ObjectTypeDefinition braidOperationType,
            Function<BraidSchemaSource, Optional<ObjectTypeDefinition>> findOperationType,
            BiFunction<BraidSchemaSource, String, Optional<FieldRename>> getFieldRename,
            BatchLoaderEnvironment batchLoaderEnvironment) {

        return findOperationType.apply(source)
                .map(operationType ->
                        addSchemaSourceTopLevelFieldsToOperation(
                                source,
                                braidOperationType,
                                operationType,
                                getFieldRename,
                                batchLoaderEnvironment))
                .orElse(emptyList());
    }

    private static List<FieldDataLoaderRegistration> addSchemaSourceTopLevelFieldsToOperation(
            BraidSchemaSource schemaSource,
            ObjectTypeDefinition braidOperationType,
            ObjectTypeDefinition sourceOperationType,
            BiFunction<BraidSchemaSource, String, Optional<FieldRename>> getFieldRename,
            BatchLoaderEnvironment batchLoaderEnvironment) {

        // todo: smarter merge, optional namespacing, etc
        final List<RenamedFieldDefinition> fieldDefinitions = renamedFieldDefinitions(schemaSource, sourceOperationType, getFieldRename);

        final List<FieldDefinition> braidOperationTypeFieldDefinitions = braidOperationType.getFieldDefinitions();
        fieldDefinitions.forEach(bfd -> braidOperationTypeFieldDefinitions.add(bfd.definition));

        return wireOperationFields(braidOperationType.getName(), schemaSource, fieldDefinitions, batchLoaderEnvironment);
    }

    private static List<RenamedFieldDefinition> renamedFieldDefinitions(BraidSchemaSource schemaSource,
                                                                        ObjectTypeDefinition sourceOperationType,
                                                                        BiFunction<BraidSchemaSource, String, Optional<FieldRename>> getFieldRename) {
        return sourceOperationType.getFieldDefinitions().stream()
                .map(definition -> {
                    Optional<FieldRename> optionalFieldRename = getFieldRename.apply(schemaSource, definition.getName());
                    return optionalFieldRename
                            .map(fieldRename -> new RenamedFieldDefinition(fieldRename, definition))
                            .orElseGet(() -> new RenamedFieldDefinition(FieldRename.from(definition.getName(), definition.getName()), definition));
                })
                .map(def -> renamedFieldDefinition(schemaSource, def))
                .collect(toList());
    }

    private static RenamedFieldDefinition renamedFieldDefinition(BraidSchemaSource schemaSource, RenamedFieldDefinition renamedFieldDefinition) {
        final FieldDefinition definition = renamedFieldDefinition.definition;
        Type renamedType = schemaSource.renameTypeToBraidName(definition.getType());
        return new RenamedFieldDefinition(
                renamedFieldDefinition.fieldRename,
                FieldDefinition.newFieldDefinition()
                        .name(renamedFieldDefinition.fieldRename.getBraidName())
                        .type(renamedType)
                        .inputValueDefinitions(schemaSource.renameInputValueDefinitionsToBraidTypes(definition.getInputValueDefinitions()))
                        .directives(definition.getDirectives()).build());
    }

    private static List<FieldDataLoaderRegistration> wireOperationFields(String typeName, BraidSchemaSource schemaSource,
                                                                         List<RenamedFieldDefinition> fieldDefinitions,
                                                                         BatchLoaderEnvironment batchLoaderEnvironment) {
        return fieldDefinitions.stream()
                .map(queryField -> wireOperationField(typeName, schemaSource, queryField, batchLoaderEnvironment))
                .collect(toList());
    }

    private static FieldDataLoaderRegistration wireOperationField(
            String typeName,
            BraidSchemaSource schemaSource,
            RenamedFieldDefinition operationField,
            BatchLoaderEnvironment batchLoaderEnvironment) {

        String sourceType = schemaSource.getSourceTypeName(unwrap(operationField.definition.getType()));
        TopLevelFieldTransformation fieldTransformation = new TopLevelFieldTransformation(
                operationField.fieldRename, schemaSource.getExtensions(sourceType));
        BatchLoader<DataFetchingEnvironment, DataFetcherResult<Object>> batchLoader =
                schemaSource.getSchemaSource().newBatchLoader(
                        schemaSource.getSchemaSource(),
                        fieldTransformation,
                        batchLoaderEnvironment);

        return new FieldDataLoaderRegistration(typeName, operationField.fieldRename.getBraidName(), batchLoader);
    }

    private static final class RenamedFieldDefinition {
        private final FieldRename fieldRename;
        private final FieldDefinition definition;

        private RenamedFieldDefinition(FieldRename fieldRename, FieldDefinition definition) {
            this.fieldRename = fieldRename;
            this.definition = definition;
        }
    }

    /**
     * This is an intermediate data structure to hold the BatchLoader for a field of a type.
     */
    private static class FieldDataLoaderRegistration {
        private final String type;
        private final String field;
        private final BatchLoader<DataFetchingEnvironment, DataFetcherResult<Object>> loader;

        private FieldDataLoaderRegistration(String type, String field, BatchLoader<DataFetchingEnvironment, DataFetcherResult<Object>> loader) {
            this.type = requireNonNull(type);
            this.field = requireNonNull(field);
            this.loader = requireNonNull(loader);
        }
    }

    static class TopLevelDataFetcher implements DataFetcher<CompletableFuture<DataFetcherResult<Object>>> {

        private final String type;
        private final String field;

        TopLevelDataFetcher(String type, String field) {
            this.type = type;
            this.field = field;
        }

        @Override
        public CompletableFuture<DataFetcherResult<Object>> get(DataFetchingEnvironment env) {
            DataLoader<DataFetchingEnvironment, DataFetcherResult<Object>> dataLoader = env.getDataLoader(getDataLoaderKey(type, field));
            final CompletableFuture<DataFetcherResult<Object>> loadedValue = dataLoader.load(env);

            // allows a top level field to also be linked
            DataLoader<DataFetchingEnvironment, DataFetcherResult<Object>> linkDataLoader = env.getDataLoader(getLinkDataLoaderKey(type, field));
            return linkDataLoader != null
                    ? loadFromLinkLoader(env, loadedValue, linkDataLoader)
                    : loadedValue;
        }

        private static CompletableFuture<DataFetcherResult<Object>> loadFromLinkLoader(DataFetchingEnvironment env,
                                                                    Object source,
                                                                    DataLoader<DataFetchingEnvironment, DataFetcherResult<Object>> dataLoader) {
            DataFetchingEnvironment linkEnv = newDataFetchingEnvironment(env)
                    .source(source)
                    .fieldDefinition(env.getFieldDefinition())
                    .build();
            return dataLoader.load(linkEnv);
        }
    }

}
