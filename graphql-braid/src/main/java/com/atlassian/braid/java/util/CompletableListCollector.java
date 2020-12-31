package com.atlassian.braid.java.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * A {@link Collector} to collect values in {@link CompletionStage}s of {@link Map}s into a <code>CompletionStage</code>
 * of {@link List}. The values in the resulting list should be ordered according to the order of keys passed in.
 *
 * @param <K> The type for keys.
 * @param <V> The type for values.
 */
public class CompletableListCollector<K, V> implements Collector<CompletionStage<Map<K, V>>, List<CompletableFuture<Map<K, V>>>, CompletionStage<List<V>>> {
    private List<K> keys;

    /**
     * Creates a collector.
     *
     * @param keys The keys used to order the resulting values.
     */
    public CompletableListCollector(List<K> keys) {
        this.keys = keys;
    }

    @Override
    public Supplier<List<CompletableFuture<Map<K, V>>>> supplier() {
        return ArrayList::new;
    }

    @Override
    public BiConsumer<List<CompletableFuture<Map<K, V>>>, CompletionStage<Map<K, V>>> accumulator() {
        return (list, item) -> list.add(item.toCompletableFuture());
    }

    @Override
    public BinaryOperator<List<CompletableFuture<Map<K, V>>>> combiner() {
        return (left, right) -> {
            left.addAll(right);
            return left;
        };
    }

    @Override
    public Function<List<CompletableFuture<Map<K, V>>>, CompletionStage<List<V>>> finisher() {
        return list -> CompletableFuture.allOf(list.toArray(new CompletableFuture[list.size()]))
                .thenApply(v -> {
                            Map<K, V> results = list.stream()
                                    .map(CompletableFuture::join)
                                    // Merge the maps
                                    .map(Map::entrySet)
                                    .flatMap(Collection::stream)
                                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                            // Create the value list in the order of the keys.
                            return keys.stream()
                                    .map(results::get)
                                    .collect(Collectors.toList());
                        }

                );
    }

    @Override
    public Set<Characteristics> characteristics() {
        return Collections.emptySet();
    }
}
