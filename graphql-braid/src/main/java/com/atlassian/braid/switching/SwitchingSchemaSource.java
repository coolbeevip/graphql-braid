package com.atlassian.braid.switching;

import com.atlassian.braid.BatchLoaderEnvironment;
import com.atlassian.braid.Extension;
import com.atlassian.braid.FieldTransformation;
import com.atlassian.braid.Link;
import com.atlassian.braid.SchemaNamespace;
import com.atlassian.braid.SchemaSource;
import com.atlassian.braid.java.util.CompletableListCollector;
import com.atlassian.braid.source.AbstractSchemaSource;
import com.atlassian.braid.source.SchemaLoader;
import graphql.execution.DataFetcherResult;
import graphql.schema.DataFetchingEnvironment;
import org.dataloader.BatchLoader;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.atlassian.braid.source.SchemaUtils.loadPublicSchema;

/**
 * A switching schema source is built with multiple other delegate schema sources and a schema source selector. When
 * loading data, the BatchLoader uses the selector to determine which delegate schema source to use and delegate data
 * loading to the selected schema source.
 */
public class SwitchingSchemaSource extends AbstractSchemaSource {

    private Map<String, SchemaSource> delegates;
    private NamespaceSelector selector;

    SwitchingSchemaSource(SchemaNamespace namespace,
                          SchemaLoader schemaLoader,
                          List<SchemaSource> delegates,
                          NamespaceSelector selector,
                          List<Link> links,
                          List<Extension> extensions,
                          String... topLevelFields) {
        super(namespace, loadPublicSchema(schemaLoader, links, topLevelFields), schemaLoader.load(), links, extensions);

        this.delegates = delegates.stream()
                .collect(Collectors.toMap(
                        schemaSource -> schemaSource.getNamespace().getValue(),
                        schemaSource -> schemaSource));
        this.selector = selector;
    }

    /**
     * Creates a BatchLoader that uses the delegate schema sources to load data. When batching loading with multiple keys,
     * we need to group the keys by the schema source namespace, use the BatchLoader for each delegate schema source to
     * load the group, and combine the results.
     */
    @Override
    public BatchLoader<DataFetchingEnvironment, DataFetcherResult<Object>> newBatchLoader(SchemaSource schemaSource,
                                                                                          FieldTransformation fieldTransformation,
                                                                                          BatchLoaderEnvironment batchLoaderEnvironment) {
        return keys -> {
            // Group the keys by schema source namespace.
            Map<String, List<DataFetchingEnvironment>> keysByNamespace = keys.stream()
                    .collect(Collectors.groupingBy(env -> selector.select(env)));

            return keysByNamespace.entrySet().stream()
                    .map(entry -> {
                        String namespace = entry.getKey();
                        List<DataFetchingEnvironment> keysForNamespace = entry.getValue();

                        // Use the BatchLoader for the delegate schema source to load the data.
                        SchemaSource delegate = delegates.get(namespace);
                        BatchLoader<DataFetchingEnvironment, DataFetcherResult<Object>> batchLoader =
                                delegate.newBatchLoader(delegate, fieldTransformation, batchLoaderEnvironment);
                        CompletionStage<List<DataFetcherResult<Object>>> resultsForNamespace = batchLoader.load(keysForNamespace);

                        return resultsForNamespace.thenApply(results -> {
                            Map<DataFetchingEnvironment, DataFetcherResult<Object>> resultsMap = new HashMap<>();
                            IntStream.range(0, results.size())
                                    .forEach(i -> resultsMap.put(keysForNamespace.get(i), results.get(i)));
                            return resultsMap;
                        });
                    })
                    // The collector collects the results in the order of the keys. This is required by BatchLoader. See
                    // its Javadoc.
                    .collect(new CompletableListCollector<>(keys));
        };
    }

}
