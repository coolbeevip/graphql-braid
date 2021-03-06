package com.atlassian.braid;

import com.atlassian.braid.transformation.SchemaTransformation;
import graphql.GraphQL;
import graphql.execution.AsyncExecutionStrategy;
import graphql.execution.ExecutionId;
import graphql.execution.ExecutionIdProvider;
import graphql.execution.ExecutionStrategy;
import graphql.execution.instrumentation.ChainedInstrumentation;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.dataloader.DataLoaderDispatcherInstrumentation;
import graphql.execution.preparsed.NoOpPreparsedDocumentProvider;
import graphql.execution.preparsed.PreparsedDocumentProvider;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.dataloader.BatchLoader;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderRegistry;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.atlassian.braid.java.util.BraidLists.concat;
import static graphql.schema.idl.RuntimeWiring.newRuntimeWiring;
import static java.util.Objects.requireNonNull;

/**
 * This is the entry class for Braid to {@link #builder() build} a new {@link Braid} instance and to {@link #newGraphQL() get a new BraidGraphQL}
 * instance to execute queries.
 */
@SuppressWarnings("WeakerAccess")
public final class Braid {
    private final BraidSchema braidSchema;
    private final BraidRuntime braidRuntime;

    private final Function<BraidSchema, DataLoaderRegistry> dataLoaderRegistryFactory;
    private final Supplier<Instrumentation> dataLoaderInstrumentationFactory;


    private Braid(BraidSchema braidSchema, BraidRuntime braidRuntime,
                  Function<BraidSchema, DataLoaderRegistry> dataLoaderRegistryFactory,
                   Supplier<Instrumentation> dataLoaderInstrumentationFactory) {
        this.braidSchema = requireNonNull(braidSchema);
        this.braidRuntime = requireNonNull(braidRuntime);
        this.dataLoaderRegistryFactory = requireNonNull(dataLoaderRegistryFactory);
        this.dataLoaderInstrumentationFactory = requireNonNull(dataLoaderInstrumentationFactory);
    }

    public static BraidBuilder builder() {
        return new BraidBuilder();
    }

    public GraphQLSchema getSchema() {
        return braidSchema.getSchema();
    }

    /**
     * Builds a new GraphQL instance to run queries. Note that a new instance should be created for each new query.
     *
     * @return a new {@link BraidGraphQL} instance
     */
    @Nonnull
    public BraidGraphQL newGraphQL() {
        return newGraphQL(Function.identity());
    }

    /**
     * Builds a new GraphQL instance to run queries. Note that a new instance should be created for each new query.
     *
     * @param gqlSchemaTransformer the function to transform the schema
     *
     * @return a new {@link BraidGraphQL} instance
     */
    public BraidGraphQL newGraphQL(Function<GraphQLSchema, GraphQLSchema> gqlSchemaTransformer) {
        return new BraidGraphQL(
                () -> dataLoaderRegistryFactory.apply(braidSchema),
                () -> createGraphQl(gqlSchemaTransformer));
    }

    private GraphQL createGraphQl(Function<GraphQLSchema, GraphQLSchema> gqlSchemaTransformer) {
        GraphQLSchema inputGraphQLSchema = braidSchema.getSchema();
        GraphQLSchema graphQLSchema = requireNonNull(gqlSchemaTransformer.apply(inputGraphQLSchema));
        return newGraphQL(braidRuntime, graphQLSchema, dataLoaderInstrumentationFactory.get());
    }

    public static final class BraidBuilder {

        private TypeDefinitionRegistry typeDefinitionRegistry = new TypeDefinitionRegistry();
        private List<SchemaSource> schemaSources = new LinkedList<>();
        private BatchLoaderEnvironment batchLoaderEnvironment = null;
        private Consumer<RuntimeWiring.Builder> runtimeWiring = __ -> {
        };

        private ExecutionIdProvider executionIdProvider = (__, ___, ____) -> ExecutionId.generate();

        private ExecutionStrategy executionStrategy = null;
        private ExecutionStrategy queryExecutionStrategy = null;
        private ExecutionStrategy mutationExecutionStrategy = null;
        private ExecutionStrategy subscriptionExecutionStrategy = null;

        private List<Instrumentation> instrumentations = new LinkedList<>();
        private PreparsedDocumentProvider preparsedDocumentProvider = new NoOpPreparsedDocumentProvider();
        private List<SchemaTransformation> customSchemaTransformations = new ArrayList<>();
        private Supplier<Instrumentation> dataLoaderInstrumentationFactory = DataLoaderDispatcherInstrumentation::new;

        public BraidBuilder customSchemaTransformations(List<SchemaTransformation> customSchemaTransformations) {
            this.customSchemaTransformations = customSchemaTransformations;
            return this;
        }

        /**
         * Adds a single schema source for Braid to handle
         *
         * @param schemaSource the schema source to add
         *
         * @return {@code this} builder
         */
        public BraidBuilder schemaSource(SchemaSource schemaSource) {
            schemaSources.add(requireNonNull(schemaSource));
            return this;
        }

        /**
         * Adds a list of schema sources for Braid to handle
         *
         * @param schemaSources the collection of schema sources to add
         *
         * @return {@code this} builder
         */
        public BraidBuilder schemaSources(Collection<SchemaSource> schemaSources) {
            this.schemaSources.addAll(requireNonNull(schemaSources));
            return this;
        }

        /**
         * Sets a specific type registry that Braid should base itself on.
         * <p>This is <strong>optional</strong> and Braid will create one if necessary.
         * <p>This is something one may want to use when one defines their own local schema and type registry using GraphQL
         * Java. In that case one may prefer defining {@link com.atlassian.braid.source.QueryExecutorSchemaSource} directly.
         *
         * @param typeDefinitionRegistry the base type registry for Braid to use
         *
         * @return {@code this} builder
         *
         * @see com.atlassian.braid.source.QueryExecutorSchemaSource
         */
        public BraidBuilder typeDefinitionRegistry(TypeDefinitionRegistry typeDefinitionRegistry) {
            this.typeDefinitionRegistry = requireNonNull(typeDefinitionRegistry);
            return this;
        }

        /**
         * This allows one to affect the runtime wiring that Braid will configure given the different schema sources.
         *
         * @param runtimeWiring a consumer of {@link RuntimeWiring.Builder} to add any relevant configuration
         *
         * @return {@code this} builder
         */
        public BraidBuilder withRuntimeWiring(Consumer<RuntimeWiring.Builder> runtimeWiring) {
            this.runtimeWiring = requireNonNull(runtimeWiring);
            return this;
        }

        /**
         * Adds (in order) an instrumentation to the GraphQL instances used to run queries.
         * <p>Note that Braid already adds the necessary instrumentation to handle the required batch loading provided.
         *
         * @param instrumentation the instrumentation to add
         *
         * @return {@code this} builder
         *
         * @see #dataLoaderInstrumentationFactory(Supplier)
         */
        public BraidBuilder instrumentation(Instrumentation instrumentation) {
            this.instrumentations.add(requireNonNull(instrumentation));
            return this;
        }

        /**
         * Sets the execution ID provider for the GraphQL instance
         * <p>This is <strong>optional</strong> and Braid uses the basic {@link ExecutionId#generate()} by default.
         *
         * @param executionIdProvider the new execution ID provider
         *
         * @return {@code this} builder
         *
         * @see ExecutionId#generate()
         */
        public BraidBuilder executionIdProvider(ExecutionIdProvider executionIdProvider) {
            this.executionIdProvider = requireNonNull(executionIdProvider);
            return this;
        }

        /**
         * Sets the execution strategy to use, it will be associated to query, mutation and subscription, unless
         * otherwise overridden by the respective {@link #queryExecutionStrategy(ExecutionStrategy)},
         * <p>This is <strong>optional</strong>, in which case only the query execution strategy will be set to its
         * default
         *
         * @param executionStrategy the new execution strategy
         *
         * @return {@code this}
         */
        public BraidBuilder executionStrategy(ExecutionStrategy executionStrategy) {
            this.executionStrategy = requireNonNull(executionStrategy);
            return this;
        }

        /**
         * Sets the query execution strategy to use.
         * <p>It will be preferred to the one set via {@link #executionStrategy(ExecutionStrategy)} if both are set.
         * <p>This is <strong>optional</strong> and a new {@link AsyncExecutionStrategy} will be used if no query
         * execution strategy is configured
         *
         * @param executionStrategy the new execution strategy
         *
         * @return {@code this} builder
         */
        public BraidBuilder queryExecutionStrategy(ExecutionStrategy executionStrategy) {
            this.queryExecutionStrategy = requireNonNull(executionStrategy);
            return this;
        }

        /**
         * Sets the mutation execution strategy to use.
         * <p>It will be preferred to the one set via {@link #executionStrategy(ExecutionStrategy)} if both are set.
         * <p>This is <strong>optional</strong> and a no execution strategy for mutation will be set if not defined.
         *
         * @param executionStrategy the new execution strategy
         *
         * @return {@code this} builder
         */
        public BraidBuilder mutationExecutionStrategy(ExecutionStrategy executionStrategy) {
            this.mutationExecutionStrategy = requireNonNull(executionStrategy);
            return this;
        }

        /**
         * Sets the subscription execution strategy to use.
         * <p>It will be preferred to the one set via {@link #executionStrategy(ExecutionStrategy)} if both are set.
         * <p>This is <strong>optional</strong> and a no execution strategy for mutation will be set if not defined.
         *
         * @param executionStrategy the new execution strategy
         *
         * @return {@code this} builder
         */
        public BraidBuilder subscriptionExecutionStrategy(ExecutionStrategy executionStrategy) {
            this.subscriptionExecutionStrategy = requireNonNull(executionStrategy);
            return this;
        }

        /**
         * The preparsed document provider to use for queries to document caching and/or whitelisting
         * <p>This is <strong>optional</strong> and a {@link NoOpPreparsedDocumentProvider} will be used if not defined
         *
         * @param preparsedDocumentProvider the provider to use
         *
         * @return {@code this} builder
         *
         * @see NoOpPreparsedDocumentProvider
         */
        public BraidBuilder preparsedDocumentProvider(PreparsedDocumentProvider preparsedDocumentProvider) {
            this.preparsedDocumentProvider = requireNonNull(preparsedDocumentProvider);
            return this;
        }

        /**
         * A factory to create an instrumentation that handles DataLoader
         * <p>This is <strong>optional</strong> and a {@link DataLoaderDispatcherInstrumentation} will be used if not defined
         *
         * @param factory the factory to create an DataLoader instrumentation
         *
         * @return {@code this} builder
         *
         * @see DataLoaderDispatcherInstrumentation
         */
        public BraidBuilder dataLoaderInstrumentationFactory(Supplier<Instrumentation> factory) {
            this.dataLoaderInstrumentationFactory = requireNonNull(factory);
            return this;
        }

        /**
         * @return {@code this} builder
         */
        public BraidBuilder batchLoaderEnvironment(BatchLoaderEnvironment batchLoaderEnvironment) {
            this.batchLoaderEnvironment = batchLoaderEnvironment;
            return this;
        }



        /**
         * Builds a new Braid instance, ready to create new {@link BraidGraphQL} instances
         *
         * @return the new Braid instance
         *
         * @see BraidGraphQL
         */
        public Braid build() {
            final BraidSchema braidSchema = newBraidSchema();
            final BraidRuntime braidRuntime = newBraidRuntime();



            return new Braid(
                    braidSchema,
                    braidRuntime,
                    dataLoaderRegistryFactory(),
                     dataLoaderInstrumentationFactory);
        }

        private BraidRuntime newBraidRuntime() {
            return new BraidRuntime(
                    executionIdProvider,
                    getQueryExecutionStrategy(),
                    Optional.ofNullable(mutationExecutionStrategy).orElse(executionStrategy),
                    Optional.ofNullable(subscriptionExecutionStrategy).orElse(executionStrategy),
                    preparsedDocumentProvider,
                    instrumentations);
        }

        private ExecutionStrategy getQueryExecutionStrategy() {
            return Optional.ofNullable(
                    Optional.ofNullable(queryExecutionStrategy).orElse(executionStrategy))
                    .orElse(new AsyncExecutionStrategy());
        }

        private BraidSchema newBraidSchema() {
            return BraidSchema.from(typeDefinitionRegistry, getRuntimeWiringBuilder(), schemaSources,
                    customSchemaTransformations, batchLoaderEnvironment);
        }

        private RuntimeWiring.Builder getRuntimeWiringBuilder() {
            final RuntimeWiring.Builder runtimeWiringBuilder = newRuntimeWiring();
            runtimeWiring.accept(runtimeWiringBuilder);
            return runtimeWiringBuilder;
        }
    }

    private static GraphQL newGraphQL(BraidRuntime runtime, GraphQLSchema schema, Instrumentation dataLoaderInstrumentation) {
        requireNonNull(schema);
        final GraphQL.Builder graphQlBuilder = new GraphQL.Builder(schema)
                .executionIdProvider(runtime.executionIdProvider)
                .queryExecutionStrategy(runtime.queryExecutionStrategy)
                .preparsedDocumentProvider(runtime.preparsedDocumentProvider)
                .instrumentation(chainInstrumentationAndAddDataLoaderDispatcher(runtime.instrumentations, dataLoaderInstrumentation));

        runtime.getMutationExecutionStrategy().ifPresent(graphQlBuilder::mutationExecutionStrategy);
        runtime.getSubscriptionExecutionStrategy().ifPresent(graphQlBuilder::subscriptionExecutionStrategy);

        return graphQlBuilder.build();
    }

    private static ChainedInstrumentation chainInstrumentationAndAddDataLoaderDispatcher(
            List<Instrumentation> instrumentationList, Instrumentation dataLoaderInstrumentation) {
        return new ChainedInstrumentation(concat(
                instrumentationList,
                dataLoaderInstrumentation));
    }

    private static Function<BraidSchema, DataLoaderRegistry> dataLoaderRegistryFactory() {
        return schema -> {
            DataLoaderRegistry registry = new DataLoaderRegistry();
            schema.getBatchLoaders().forEach((key, loader) -> registry.register(key, newDataLoader(loader)));
            return registry;
        };
    }

    @SuppressWarnings("unchecked")
    private static DataLoader newDataLoader(BatchLoader loader) {
        return new DataLoader(loader);
    }

    private static final class BraidRuntime {
        private final ExecutionIdProvider executionIdProvider;
        private final ExecutionStrategy queryExecutionStrategy;
        private final ExecutionStrategy mutationExecutionStrategy;
        private final ExecutionStrategy subscriptionExecutionStrategy;
        private final PreparsedDocumentProvider preparsedDocumentProvider;

        private List<Instrumentation> instrumentations = new LinkedList<>();

        private BraidRuntime(ExecutionIdProvider executionIdProvider,
                             ExecutionStrategy queryExecutionStrategy,
                             ExecutionStrategy mutationExecutionStrategy, // nullable
                             ExecutionStrategy subscriptionExecutionStrategy, // nullable
                             PreparsedDocumentProvider preparsedDocumentProvider,
                             List<Instrumentation> instrumentations) {
            this.executionIdProvider = requireNonNull(executionIdProvider);
            this.queryExecutionStrategy = requireNonNull(queryExecutionStrategy);
            this.mutationExecutionStrategy = mutationExecutionStrategy;
            this.subscriptionExecutionStrategy = subscriptionExecutionStrategy;
            this.preparsedDocumentProvider = requireNonNull(preparsedDocumentProvider);
            this.instrumentations = requireNonNull(instrumentations);
        }

        public Optional<ExecutionStrategy> getMutationExecutionStrategy() {
            return Optional.ofNullable(mutationExecutionStrategy);
        }

        public Optional<ExecutionStrategy> getSubscriptionExecutionStrategy() {
            return Optional.ofNullable(subscriptionExecutionStrategy);
        }
    }

}
