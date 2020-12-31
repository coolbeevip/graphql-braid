package com.atlassian.braid.switching

import com.atlassian.braid.BraidContext
import com.atlassian.braid.SchemaNamespace
import com.atlassian.braid.SchemaSource
import com.atlassian.braid.source.SchemaLoader
import com.atlassian.braid.source.StringSchemaLoader
import com.atlassian.braid.source.yaml.HttpRestRemoteRetriever
import com.atlassian.braid.source.yaml.RestRemoteSchemaSource
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLFieldDefinition
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test

import static groovy.json.JsonOutput.toJson
import static org.mockito.Mockito.mock
import static org.mockito.Mockito.when

class SwitchingSchemaSourceTest {

    static SchemaSource createSchemaSource() {
        final SCHEMA = """
            schema {
                query: Query
            }
            type Query {
                foo(id: String): Foo
            } 
            type Foo {
                foo: String
            }
        """.stripIndent()
        def schemaLoader = new StringSchemaLoader(SchemaLoader.Type.IDL, SCHEMA)
        def remoteRetriever = new HttpRestRemoteRetriever()
        def fooMapper = { sourceMap -> ["foo": sourceMap.get("foo")] }

        def server1 = new MockWebServer()
        server1.enqueue(new MockResponse().setBody(toJson(["foo": "bar1"])))
        server1.enqueue(new MockResponse().setBody(toJson(["foo": "bar1"])))
        server1.start()
        def url1 = server1.url("/{id}")

        def fooRootField1 = new RestRemoteSchemaSource.RootField("foo", "${url1}", fooMapper)
        def rootFields1 = ["foo": fooRootField1]
        def restSchemaSource1 = new RestRemoteSchemaSource(
                SchemaNamespace.of("namespace0"),
                schemaLoader,
                remoteRetriever,
                rootFields1,
                [],
                [],
                "foo"
        )

        def server2 = new MockWebServer()
        server2.enqueue(new MockResponse().setBody(toJson(["foo": "bar2"])))
        server2.enqueue(new MockResponse().setBody(toJson(["foo": "bar2"])))
        server2.start()
        def url2 = server2.url("/{id}")

        def fooRootField2 = new RestRemoteSchemaSource.RootField("foo", "${url2}", fooMapper)
        def rootFields2 = ["foo": fooRootField2]
        def restSchemaSource2 = new RestRemoteSchemaSource(
                SchemaNamespace.of("namespace1"),
                schemaLoader,
                remoteRetriever,
                rootFields2,
                [],
                [],
                "foo"
        )

        def namespaceSelector = new MockNamespaceSelector()
        def switchingSchemaSource = new SwitchingSchemaSourceBuilder()
                .namespace(SchemaNamespace.of("switching"))
                .schemaLoader(schemaLoader)
                .delegates([restSchemaSource1, restSchemaSource2])
                .selector(namespaceSelector)
                .topLevelFields("foo")
                .build()

        return switchingSchemaSource
    }

    @Test
    void loadSingle() {
        def fieldDefinition = mock(GraphQLFieldDefinition.class)
        when(fieldDefinition.getName()).thenReturn("foo")

        def braidContext = mock(BraidContext.class)

        def environment1 = createEnvironment("0", fieldDefinition, braidContext)
        def environment2 = createEnvironment("1", fieldDefinition, braidContext)

        def switchingSchemaSource = createSchemaSource()
        def batchLoader = switchingSchemaSource.newBatchLoader(switchingSchemaSource, null, null)
        def result1 = batchLoader.load([environment1])
        assert result1.get().data == [["foo": "bar1"]]
        def result2 = batchLoader.load([environment2])
        assert result2.get().data == [["foo": "bar2"]]
    }

    @Test
    void loadBatch() {
        def fieldDefinition = mock(GraphQLFieldDefinition.class)
        when(fieldDefinition.getName()).thenReturn("foo")

        def braidContext = mock(BraidContext.class)
        when(braidContext.getContext()).thenReturn(0, 1, 2, 3)

        def environment1 = createEnvironment("0", fieldDefinition, braidContext)
        def environment2 = createEnvironment("1", fieldDefinition, braidContext)
        def environment3 = createEnvironment("2", fieldDefinition, braidContext)
        def environment4 = createEnvironment("3", fieldDefinition, braidContext)

        def switchingSchemaSource = createSchemaSource()
        def batchLoader = switchingSchemaSource.newBatchLoader(switchingSchemaSource, null, null)
        def result1 = batchLoader.load([environment1, environment2, environment3, environment4])
        assert result1.get().data == [["foo": "bar1"], ["foo": "bar2"], ["foo": "bar1"], ["foo": "bar2"]]
    }

    static DataFetchingEnvironment createEnvironment(String id, GraphQLFieldDefinition fieldDefinition, BraidContext braidContext) {
        def environment = mock(DataFetchingEnvironment.class)
        when(environment.getFieldDefinition()).thenReturn(fieldDefinition)
        when(environment.getArguments()).thenReturn(['id': id])
        when(environment.getContext()).thenReturn(braidContext)

        return environment
    }
}
