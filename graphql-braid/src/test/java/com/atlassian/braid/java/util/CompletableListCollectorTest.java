package com.atlassian.braid.java.util;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.Assert.fail;

public class CompletableListCollectorTest {

    @Test
    public void testCollector() throws ExecutionException, InterruptedException {
        CompletableFuture<Map<Integer, String>> completableMap1 = new CompletableFuture<>();
        completableMap1.complete(ImmutableMap.of(
                1, "1",
                2, "2",
                3, "3")
        );
        CompletableFuture<Map<Integer, String>> completableMap2 = new CompletableFuture<>();
        completableMap2.complete(ImmutableMap.of(
                4, "4",
                5, "5",
                6, "6")
        );
        CompletableFuture<Map<Integer, String>> completableMap3 = new CompletableFuture<>();
        completableMap3.complete(ImmutableMap.of(
                7, "7",
                8, "8",
                9, "9")
        );

        List<Integer> keys = ImmutableList.of(4, 5, 6, 9, 1, 2, 3, 8, 7);
        CompletionStage<List<String>> actualCompletableList = ImmutableList.of(completableMap1, completableMap2, completableMap3)
                .stream()
                .collect(new CompletableListCollector<>(keys));

        List<String> actualList = actualCompletableList.toCompletableFuture().get();
        assertThat(actualList).isEqualTo(ImmutableList.of("4", "5", "6", "9", "1", "2", "3", "8", "7"));
    }

    @Test
    public void testCollectorDuplicateKey() throws Exception {
        CompletableFuture<Map<Integer, String>> completableMap1 = new CompletableFuture<>();
        completableMap1.complete(ImmutableMap.of(
                1, "1",
                2, "2",
                3, "3")
        );
        CompletableFuture<Map<Integer, String>> completableMap2 = new CompletableFuture<>();
        completableMap2.complete(ImmutableMap.of(
                4, "4",
                5, "5",
                1, "duplicate key 1")
        );
        List<Integer> keys = ImmutableList.of(4, 5, 1, 2, 3);
        CompletionStage<List<String>> actualCompletableList = ImmutableList.of(completableMap1, completableMap2)
                .stream()
                .collect(new CompletableListCollector<>(keys));

        try {
            actualCompletableList.toCompletableFuture().get();
            fail("Exception expected because of duplicate key");
        }
        catch (ExecutionException e) {
            assertThat(e).hasCauseInstanceOf(IllegalStateException.class);
        }
    }

}
