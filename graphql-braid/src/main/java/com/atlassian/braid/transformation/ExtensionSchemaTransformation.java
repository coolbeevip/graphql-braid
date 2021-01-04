package com.atlassian.braid.transformation;

import static com.atlassian.braid.TypeUtils.unwrap;
import static graphql.schema.DataFetchingEnvironmentImpl.newDataFetchingEnvironment;
import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import com.atlassian.braid.Extension;
import com.atlassian.braid.SchemaSource;
import com.atlassian.braid.graphql.language.KeyedDataFetchingEnvironment;
import com.atlassian.braid.java.util.BraidObjects;
import graphql.execution.DataFetcherResult;
import graphql.execution.MergedField;
import graphql.language.Field;
import graphql.language.FieldDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.TypeDefinition;
import graphql.language.TypeName;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLSchema;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import org.dataloader.BatchLoader;

/**
 * A {@link SchemaTransformation} for processing extensions, which add fields to source object types. The fields to add
 * are the ones in the target object type, which is specified in the {@link Extension}. The field values are loaded
 * from a top-level query field of the target schema source.
 */
public class ExtensionSchemaTransformation implements SchemaTransformation {
    @Override
    public Map<String, BatchLoader> transform(BraidingContext ctx) {
        return ctx.getRegistry().types().values().stream()
                .flatMap(typeDef -> BraidTypeDefinition.getFieldDefinitions(typeDef).stream()
                        .flatMap(fieldDef -> ctx.getDataSources().values().stream()
                                .filter(ds -> ds.hasTypeAndField(ctx.getRegistry(), typeDef, fieldDef))
                                .flatMap(ds -> ds.getExtensions(ds.getSourceTypeName(unwrap(fieldDef.getType()))).stream()
                                        .map(ext -> mergeType(ds, ctx, typeDef, fieldDef, ext))
                                        .flatMap(m -> m.entrySet().stream())
                                )))
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Map<String, BatchLoader<DataFetchingEnvironment, DataFetcherResult<Object>>> mergeType(BraidSchemaSource ds,
                                                                                                   BraidingContext ctx,
                                                                                                   TypeDefinition typeDef,
                                                                                                   FieldDefinition fieldDef,
                                                                                                   Extension ext) {
        ObjectTypeDefinition originalType = findRequiredOriginalType(ctx, ds, fieldDef);

        Set<String> originalTypeFieldNames = originalType.getFieldDefinitions().stream().map(FieldDefinition::getName).collect(toSet());

        BraidSchemaSource targetSource = ctx.getDataSources().get(ext.getBy().getNamespace());

        ObjectTypeDefinition targetType = findRequiredTargetType(targetSource, ext);

        String key = "ext-" + typeDef.getName();

        // Add all the fields of the target object type to the source object type.
        wireNewFields(ctx, ds, typeDef, fieldDef, ext, originalType, originalTypeFieldNames, targetType, key);

        // All the fields added share a common BatchLoader that loads the top-level field of the target schema source.
        SchemaSource schemaSource = ctx.getDataSources().get(ext.getBy().getNamespace()).getSchemaSource();
        return singletonMap(key, schemaSource.newBatchLoader(schemaSource, new ExtensionTransformation(ext), ctx.getBatchLoaderEnvironment()));
    }

    private ObjectTypeDefinition findRequiredTargetType(BraidSchemaSource targetSource, Extension ext) {
        return (ObjectTypeDefinition) targetSource.getType(ext.getBy().getType()).orElseThrow(IllegalAccessError::new);
    }

    private ObjectTypeDefinition findRequiredOriginalType(BraidingContext ctx, BraidSchemaSource braidSchemaSource, FieldDefinition fieldDef) {
        return (ObjectTypeDefinition) ctx.getRegistry().getType(braidSchemaSource.getBraidTypeName(unwrap(fieldDef.getType())))
                .orElseThrow(IllegalArgumentException::new);
    }

    /**
     * Adds all the fields of the target object type to the source object type and register DataFetchers for them.
     */
    private void wireNewFields(BraidingContext ctx,
                               BraidSchemaSource ds,
                               TypeDefinition typeDef,
                               FieldDefinition fieldDef,
                               Extension ext,
                               ObjectTypeDefinition originalType,
                               Set<String> originalTypeFieldNames,
                               ObjectTypeDefinition targetType,
                               String key) {
        targetType.getFieldDefinitions().stream()
                // Fields that already exist in the source object type are omitted.
                .filter(fd -> !originalTypeFieldNames.contains(fd.getName()))
                .forEach(fd -> {
                    originalType.getFieldDefinitions().add(fd);
                    ctx.registerDataFetcher(ds.getBraidTypeName(ext.getType()), fd.getName(), buildDataFetcher(ds, typeDef, fieldDef, key, fd));
                });
    }

    /**
     * The DataFetcher for the extension fields share a common BatchLoader used by loading the top-level field
     * containing the extension fields.
     */
    private DataFetcher buildDataFetcher(BraidSchemaSource ds, TypeDefinition typeDef, FieldDefinition fieldDef, String key, FieldDefinition fd) {
        return env -> env.getDataLoader(key)
                // Load the top-level field containing the extension fields
                .load(new KeyedDataFetchingEnvironment(updateDataFetchingEnvironment(ds, typeDef, fieldDef, env)))
                .thenApply(BraidObjects::<DataFetcherResult<Map<String, Object>>>cast)
                // Get the individual extension field value from the containing top-level field value.
                .thenApply(dfr -> nullSafeGetFieldValue(dfr, fd.getName()));
    }

    private static Object nullSafeGetFieldValue(@Nullable DataFetcherResult<Map<String, Object>> dfr, String fieldName) {
        return Optional.ofNullable(dfr)
                .flatMap(r -> Optional.ofNullable(r.getData()))
                .map(data -> data.get(fieldName))
                .orElse(null);
    }

    private static DataFetchingEnvironment updateDataFetchingEnvironment(BraidSchemaSource ds,
                                                                         TypeDefinition typeDef,
                                                                         FieldDefinition fieldDef,
                                                                         DataFetchingEnvironment env) {
        final GraphQLSchema graphQLSchema = env.getGraphQLSchema();
        return newDataFetchingEnvironment(env)
                .source(env.getSource())
                .fieldDefinition(graphQLSchema.getObjectType(ds.getBraidTypeName(typeDef.getName())).getFieldDefinition(fieldDef.getName()))
                .mergedField(MergedField.newMergedField(Field.newField(fieldDef.getName()).build()).build())
                .fieldType(graphQLSchema.getObjectType(((TypeName) fieldDef.getType()).getName()))
                .parentType(graphQLSchema.getObjectType("Query"))
                .build();
    }
}
