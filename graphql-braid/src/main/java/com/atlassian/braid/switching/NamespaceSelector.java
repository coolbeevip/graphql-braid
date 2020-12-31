package com.atlassian.braid.switching;

import graphql.schema.DataFetchingEnvironment;

/**
 * A namespace selector based on the DataFetchingEnvironment.
 */
@FunctionalInterface
public interface NamespaceSelector {
    String select(DataFetchingEnvironment environment);
}
