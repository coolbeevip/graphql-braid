package com.atlassian.braid.switching

import graphql.schema.DataFetchingEnvironment

class MockNamespaceSelector implements NamespaceSelector {

    /**
     * Selects "namespace0" for even id argument and "namespace1" for odd id argument.
     */
    @Override
    String select(DataFetchingEnvironment environment) {
        int id = environment.arguments["id"] as int
        return "namespace${id % 2}"
    }
}
