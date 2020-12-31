package com.atlassian.braid.switching;

import com.atlassian.braid.SchemaNamespace;
import com.atlassian.braid.SchemaSource;
import com.atlassian.braid.source.SchemaLoader;

import java.util.List;

import static java.util.Collections.emptyList;

/**
 *  A builder for {@link SwitchingSchemaSource}.
 */
public class SwitchingSchemaSourceBuilder {

    private SchemaNamespace namespace;
    private SchemaLoader loader;
    private List<SchemaSource> delegates;
    private NamespaceSelector selector;
    private String[] topLevelFields;

    public SwitchingSchemaSourceBuilder() {
    }

    public SwitchingSchemaSourceBuilder namespace(SchemaNamespace namespace) {
        this.namespace = namespace;
        return this;
    }

    public SwitchingSchemaSourceBuilder schemaLoader(SchemaLoader loader) {
        this.loader = loader;
        return this;
    }

    public SwitchingSchemaSourceBuilder delegates(List<SchemaSource> delegates) {
        this.delegates = delegates;
        return this;
    }

    public SwitchingSchemaSourceBuilder selector(NamespaceSelector selector) {
        this.selector = selector;
        return this;
    }

    public SwitchingSchemaSourceBuilder topLevelFields(String... topLevelFields) {
        this.topLevelFields = topLevelFields;
        return this;
    }

    public SwitchingSchemaSource build() {
        return new SwitchingSchemaSource(
                namespace,
                loader,
                delegates,
                selector,
                emptyList(),
                emptyList(),
                topLevelFields
        );
    }
}
