package com.atlassian.braid;

import com.atlassian.braid.java.util.BraidObjects;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLType;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.rules.Timeout.seconds;

public class BraidTest {

    @Rule
    public final TestRule timeoutRule = new DisableOnDebug(seconds(1));

    @Rule
    public final YamlBraidExecutionRule braidRule = new YamlBraidExecutionRule();

    @Test
    public void testBraidWithMutation() {
    }

    @Test
    public void testBraidWithMutationAndObjectInput() {
    }

    @Test
    public void testBraidWithMutationAndTwoSchemaSources() {
    }

    @Test
    public void testBraidWithMultipleSameAliasedQueries() {
    }

    @Test
    public void testBraidWithExtraVariables() {
    }

    @Test
    public void testBraidWithTwoSchemaSources() {
    }

    @Test
    public void testBraidWithSchemaSourceError() {
    }

    @Test
    public void testBraidWithInlineVariables() {
    }

    @Test
    public void testBraidWithFragment() {
    }

    @Test
    public void testBraidWithFragmentAndVariables() {
    }

    @Test
    public void testBraidWithMultipleNodesUsingFragment() {
    }

    @Test
    public void testBraidWithNamedOperation() {
    }

    @Test
    public void testBraidWithTypename() {
    }

    @Test
    public void testBraidWithLinkFromSiblingField() {
    }

    @Test
    public void testBraidWithLinkFromSiblingFieldButNoFromFieldInQuery() {
    }

    @Test
    public void testBraidWithLinkOnlyQueryingID() {
    }

    @Test
    public void testBraidBatchingWithLink() {
    }

    @Test
    public void testBraidWithLinkOfIds() {
    }

    @Test
    public void testBraidWithLinkOfNullIds() {
    }

    @Test
    public void testBraidWithLinkOfNotNullIds() {
    }

    @Test
    public void testBraidWithNonStringId() {
    }

    @Test
    public void testBraidWithLinkFromReplaceTopLevelField() {
    }

    @Test
    public void testBraidWithLinkFromReplaceTopLevelFieldOfList() {
    }

    @Test
    public void testBraidWithLinkFromReplaceTopLevelFieldWithDifferentQueryNames() {
    }

    @Test
    public void testBraidWithLinkFromReplaceTopLevelFieldSameSource() {
    }

    @Test
    public void testBraidWithVariableInList() {}

    @Test
    public void testBraidWithLinkFromReplaceField() {
        Optional<GraphQLType> fooType = braidRule.braid.getSchema().getAllTypesAsList().stream()
                .filter(t -> t.getName().equals("Foo")).findAny();

        assertThat(fooType).isPresent().containsInstanceOf(GraphQLObjectType.class);

        Optional<GraphQLFieldDefinition> idField = fooType
                .map(BraidObjects::<GraphQLObjectType>cast)
                .flatMap(t -> t.getFieldDefinitions().stream().filter(f -> f.getName().equals("id")).findAny());

        assertThat(idField).isEmpty();
    }

    @Test
    public void testBraidWithLinkWithinFragment() {
    }

    @Test
    public void testBraidWithNullFromField() {
    }

    @Test
    public void testBraidWithNullFromFieldWithNullSupport() {
    }

    @Test
    public void testBraidWithInterface() {
        assertThat(braidRule.braid.getSchema().getObjectType("Foo")
                .getInterfaces().get(0).getName()).isEqualTo("Fooable");
    }

    @Test
    public void testBraidWithInterfaceRename(){}


    @Test
    public void testBraidWithDocumentMapper() {
    }

    @Test
    public void testBraidWithDocumentMapperWithPut() {
    }

    @Test
    public void testBraidWithDocumentMapper2() {
    }

    @Test
    public void testBraidWithDocumentMapperWithWildCardCopy() {
    }

    @Test
    public void testBraidWithDocumentMapperWithList() {
    }

    @Test
    public void testBraidWithDocumentMapperWithFragment() {
    }

    @Test
    public void testBraidWithDocumentMapperWithFragmentAtRoot() {
    }

    @Test
    public void testBraidWithDocumentMapperWithMultipleFragments() {
    }

    @Test
    public void testBraidWithDocumentMapperWithInlineFragment() {
    }

    @Test
    public void testBraidWithDocumentMapperWithFragmentsAndLinks() {
    }

    @Test
    public void testBraidWithDocumentMapperWithFragmentsAndInterfacesAndLinks() {
    }

    @Test
    public void testBraidWithDocumentMapperWithFragmentsAndInterfacesAndInlineFragmentsAndLinks() {
    }

    @Test
    public void testBraidWithMultipleQueryAndOperationName() {
    }

    @Test
    public void testBraidWithAliasedVariable() {
    }

    @Test
    public void testBraidWithVariableInDirective() {
    }

    @Test
    public void testBraidWithNameConflicts() {
    }

    @Test
    public void testBraidWithTopLevelFields() {
    }

    @Test
    public void testBraidWithTopLevelFieldAlias() {
    }

    @Test
    public void testBraidWithPrivateSchema() {
    }

    @Test
    public void testSimpleTypeMerging() {
    }

    @Test
    public void testSimpleTypeMergingWithAliases() {
    }

    @Test
    public void testSimpleTypeMergingWithNoId() {
    }

    @Test
    public void testBraidWithTreeOfIdsToSecondSource() {}

    @Test
    public void testBraidWithTreeOfIdsToSecondSourceWithFragment() {}

    @Test
    public void testBraidWithTreeOfIdsToSecondSourceWithInlineFragment() {}

    @Test
    public void testBraidWithTypeRename(){}

    @Test
    public void testBraidWithFieldRenameAndQueryFieldReturnsScalarType(){}

    @Test
    public void testBraidWithTypeRenameAndLink(){ }

    @Test
    public void testBraidWithComplexLink(){ }

    @Test
    public void testBraidWithTypeRenameAndLinkWithTargetNullable(){
        verifyBarFieldNonNullability("Foo2", false);
    }

    @Test
    public void testBraidWithComplexLinkWithTargetNullable(){
        verifyBarFieldNonNullability("Foo", false);
    }

    @Test
    public void testBraidWithComplexLinkWithNonNullableTargetTypeReturnNull(){
        verifyBarFieldNonNullability("Foo", true);
    }

    @Test
    public void testBraidWithComplexLinkWithNonNullableTargetTypeSucceeds() {
        verifyBarFieldNonNullability("Foo", true);
    }

    @Test
    public void testBraidWithTypeRenameAndLinkWithNonNullTargetTypeSuccess() {
        verifyBarFieldNonNullability("Foo2", true);
    }

    @Test
    public void testBraidWithTypeRenameAndLinkWithNonNullTargetTypeAndNullTargetValue() {
        verifyBarFieldNonNullability("Foo2", true);
    }

    @Test
    public void testBraidWithCustomScalars(){}

    private void verifyBarFieldNonNullability(String fooTypeName, boolean isBarFieldNonNull) {
        Optional<GraphQLType> fooType = braidRule.braid.getSchema().getAllTypesAsList().stream()
                .filter(t -> t.getName().equals(fooTypeName)).findAny();

        assertThat(fooType).isPresent().containsInstanceOf(GraphQLObjectType.class);

        Optional<GraphQLFieldDefinition> barField = fooType
                .map(BraidObjects::<GraphQLObjectType>cast)
                .flatMap(t -> t.getFieldDefinitions().stream().filter(f -> f.getName().equals("bar")).findAny());

        GraphQLFieldDefinition barFieldType = barField.get();
        if (isBarFieldNonNull) {
            assertThat(barFieldType.getType()).isInstanceOf(GraphQLNonNull.class);
            GraphQLNonNull nonNullBarField = (GraphQLNonNull) barFieldType.getType();
            assertThat(nonNullBarField.getWrappedType().getName()).isEqualTo("Bar");
        } else {
            assertThat(barFieldType.getType()).isNotInstanceOf(GraphQLNonNull.class);
        }

    }
}
