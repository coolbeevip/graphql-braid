package com.atlassian.braid.source;

import com.atlassian.braid.Extension;
import com.atlassian.braid.Link;
import com.atlassian.braid.SchemaNamespace;
import com.atlassian.braid.SchemaSource;
import graphql.schema.idl.TypeDefinitionRegistry;

import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;

public abstract class AbstractSchemaSource implements SchemaSource {
    private final SchemaNamespace namespace;
    private final TypeDefinitionRegistry schema;
    private final TypeDefinitionRegistry privateSchema;
    private final List<Link> links;
    private final List<Extension> extensions;

    public AbstractSchemaSource(SchemaNamespace namespace,
                                TypeDefinitionRegistry schema,
                                TypeDefinitionRegistry privateSchema,
                                List<Link> links,
                                List<Extension> extensions) {
        this.namespace = requireNonNull(namespace);
        this.schema = requireNonNull(schema);
        this.privateSchema = requireNonNull(privateSchema);
        this.links = new ArrayList<>(requireNonNull(links));
        this.extensions = new ArrayList<>(requireNonNull(extensions));
    }

    @Override
    public final SchemaNamespace getNamespace() {
        return namespace;
    }

    @Override
    public final TypeDefinitionRegistry getSchema() {
        return schema;
    }

    @Override
    public final TypeDefinitionRegistry getPrivateSchema() {
        return privateSchema;
    }

    @Override
    public final List<Link> getLinks() {
        return new ArrayList<>(links);
    }

    @Override
    public final List<Extension> getExtensions() {
        return new ArrayList<>(extensions);
    }
}