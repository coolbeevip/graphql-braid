package com.atlassian.braid;

import static java.util.concurrent.CompletableFuture.completedFuture;

import graphql.schema.DataFetchingEnvironment;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class DefaultArgumentValueProvider implements ArgumentValueProvider {
    public static final ArgumentValueProvider INSTANCE = new DefaultArgumentValueProvider();

    @Override
    public CompletableFuture<Object> fetchValueForArgument(LinkArgument linkArgument, DataFetchingEnvironment environment) {
        switch (linkArgument.getArgumentSource()) {
            case OBJECT_FIELD:
                return BatchLoaderUtils.getFieldValue(environment, linkArgument.getSourceName())
                        .thenApply(v -> v.orElse(null));
            case CONTEXT:
                Map<String,Object> context = (Map<String, Object>) environment.getVariables().get(linkArgument.getSourceName());
                return completedFuture(context);
//                throw new UnsupportedOperationException("not supported in default implementation");
            case FIELD_ARGUMENT:
                return completedFuture(environment.getArgument(linkArgument.getSourceName()));
            default:
                throw new UnsupportedOperationException("Unsupported argument source.");
        }
    }
}
