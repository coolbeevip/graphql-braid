package com.atlassian.braid.graphql.language;

import graphql.cachecontrol.CacheControl;
import graphql.execution.ExecutionId;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.MergedField;
import graphql.execution.directives.QueryDirectives;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.OperationDefinition;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingFieldSelectionSet;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderRegistry;


/**
 * {@link DataFetchingEnvironment} that is identified solely by its source object
 */
public class KeyedDataFetchingEnvironment implements DataFetchingEnvironment {

  private final DataFetchingEnvironment delegate;
  private final Object source;

  public KeyedDataFetchingEnvironment(DataFetchingEnvironment delegate) {
    this.delegate = delegate;
    this.source = delegate.getSource();
  }

  public <T> T getSource() {
    return delegate.getSource();
  }

  public Map<String, Object> getArguments() {
    return delegate.getArguments();
  }

  public boolean containsArgument(String name) {
    return delegate.containsArgument(name);
  }

  public <T> T getArgument(String name) {
    return delegate.getArgument(name);
  }

  @Override
  public <T> T getArgumentOrDefault(String name, T defaultValue) {
    return delegate.getArgumentOrDefault(name, defaultValue);
  }

  public <T> T getContext() {
    return delegate.getContext();
  }

  @Override
  public <T> T getLocalContext() {
    return delegate.getLocalContext();
  }

  public <T> T getRoot() {
    return delegate.getRoot();
  }

  public GraphQLFieldDefinition getFieldDefinition() {
    return delegate.getFieldDefinition();
  }

  public List<Field> getFields() {
    return delegate.getMergedField().getFields();
  }

  @Override
  public MergedField getMergedField() {
    return delegate.getMergedField();
  }

  public Field getField() {
    return delegate.getField();
  }

  public GraphQLOutputType getFieldType() {
    return delegate.getFieldType();
  }

  public ExecutionStepInfo getExecutionStepInfo() {
    return delegate.getExecutionStepInfo();
  }

  public GraphQLType getParentType() {
    return delegate.getParentType();
  }

  public GraphQLSchema getGraphQLSchema() {
    return delegate.getGraphQLSchema();
  }

  public Map<String, FragmentDefinition> getFragmentsByName() {
    return delegate.getFragmentsByName();
  }

  public ExecutionId getExecutionId() {
    return delegate.getExecutionId();
  }

  public DataFetchingFieldSelectionSet getSelectionSet() {
    return delegate.getSelectionSet();
  }

  @Override
  public QueryDirectives getQueryDirectives() {
    return delegate.getQueryDirectives();
  }

  @Override
  public <K, V> DataLoader<K, V> getDataLoader(String s) {
    return delegate.getDataLoader(s);
  }

  @Override
  public DataLoaderRegistry getDataLoaderRegistry() {
    return delegate.getDataLoaderRegistry();
  }

  @Override
  public CacheControl getCacheControl() {
    return delegate.getCacheControl();
  }

  @Override
  public Locale getLocale() {
    return delegate.getLocale();
  }

  @Override
  public OperationDefinition getOperationDefinition() {
    return delegate.getOperationDefinition();
  }

  @Override
  public Document getDocument() {
    return delegate.getDocument();
  }

  @Override
  public Map<String, Object> getVariables() {
    return delegate.getVariables();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    KeyedDataFetchingEnvironment that = (KeyedDataFetchingEnvironment) o;
    return Objects.equals(getSource(), that.getSource());
  }

  @Override
  public int hashCode() {
    return Objects.hash(source);
  }
}
