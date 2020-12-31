package com.atlassian.braid.transformation;

import com.atlassian.braid.SchemaNamespace;
import com.atlassian.braid.BatchLoaderEnvironment;
import graphql.language.ObjectTypeDefinition;
import graphql.schema.DataFetcher;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.TypeDefinitionRegistry;

import java.util.Map;


/**
 * Context information used by {@link SchemaTransformation} instances when building the BraidSchema. Among other
 * static information, it holds {@link graphql.language.TypeDefinition}s
 * in {@link TypeDefinitionRegistry}, top-level {@link graphql.language.FieldDefinition}s in {@link ObjectTypeDefinition}s
 * for root query and mutation, and {@link DataFetcher}s in {@link RuntimeWiring.Builder} while they are being
 * collected by <code>SchemaTransformation</code>.
 */
public class BraidingContext {

    private final Map<SchemaNamespace, BraidSchemaSource> dataSources;
    private final TypeDefinitionRegistry registry;
    private final RuntimeWiring.Builder runtimeWiringBuilder;
    private final ObjectTypeDefinition queryObjectTypeDefinition;
    private final ObjectTypeDefinition mutationObjectTypeDefinition;
    private final BatchLoaderEnvironment batchLoaderEnvironment;

    public BraidingContext(Map<SchemaNamespace, BraidSchemaSource> dataSources,
                           TypeDefinitionRegistry registry,
                           RuntimeWiring.Builder runtimeWiringBuilder,
                           ObjectTypeDefinition queryObjectTypeDefinition,
                           ObjectTypeDefinition mutationObjectTypeDefinition,
                           BatchLoaderEnvironment batchLoaderEnvironment) {

        this.dataSources = dataSources;
        this.registry = registry;
        this.runtimeWiringBuilder = runtimeWiringBuilder;
        this.queryObjectTypeDefinition = queryObjectTypeDefinition;
        this.mutationObjectTypeDefinition = mutationObjectTypeDefinition;
        this.batchLoaderEnvironment = batchLoaderEnvironment;
    }

    public Map<SchemaNamespace, BraidSchemaSource> getDataSources() {
        return dataSources;
    }

    public TypeDefinitionRegistry getRegistry() {
        return registry;
    }

    /**
     * Gets the open builder for registering types and data fetchers for their fields. The builder is only closed after
     * all the SchemaTransformations has been applied.
     */
    public RuntimeWiring.Builder getRuntimeWiringBuilder() {
        return runtimeWiringBuilder;
    }

    /**
     * A convenience method for registering a data fetcher for a field of a type.
     */
    void registerDataFetcher(String typeName, String fieldName, DataFetcher<?> dataFetcher) {
        runtimeWiringBuilder.type(typeName, builder -> builder.dataFetcher(fieldName, dataFetcher));
    }

    public ObjectTypeDefinition getQueryObjectTypeDefinition() {
        return queryObjectTypeDefinition;
    }

    public ObjectTypeDefinition getMutationObjectTypeDefinition() {
        return mutationObjectTypeDefinition;
    }

    BatchLoaderEnvironment getBatchLoaderEnvironment(){
        return batchLoaderEnvironment;
    }
}
