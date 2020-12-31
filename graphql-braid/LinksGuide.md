# Links

A link is used to add a field to a source type in the source namespace. At runtime, the value of the field is populated with the value of a top-level field in the target namespace. Typically, the query to the target namespace uses a field in the source type as argument.

## A Simple Exmaple
The following is a simple example. The source namespace `content` has the following schema.
```graphql
schema {
    query: ContentQuery
}

type ContentQuery {
   page(id: String): Page
}

type Page {
    id: String
    title: String
    authorId: String
}
```

The target namespace `identity` has the following schema.
```
schema {
    query: IdentityQuery
}

type IdentityQuery {
    user(id: String): User
}

type User {
    userId: String
    name: String
}
```

A link definition produces a the following scehma, adding `author` field to type `Page`.
```
schema {
    query: Query
}

type Query {
   page(id: String): Page
}

type Page {
    id: String
    title: String
    author: User # This is the linked field.
}

type User {
    userId: String
    name: String
}
```

It allows a query like the following whose response contains values from both namesource. At runtime, the engine first queries the `page` in the `content` namespace and retreives the `title` and the `authorId` for the `Page`. Then it queries `user` in the `identity` namespace, passing `authorId` as the `id` argument.
```
query {
  page(id: "123") {
    title
    author {
      name
    }
  }
}  
```

## Simple Link Definition
A simple link definition takes the following form.
```YAML
- namespace: Content
  links: 
    - from:
        # The source type in the source namespace
        type: Page
        # The field that is added to the source type.
        field: author
        # (Optional) The field whose value is used as argument to query the target namesapce.
        # If not specified, use the field specified in "field".
        fromField: authorId
        # (Optional) Whether or not to remove the field specified in "fromField" from the source type.
        # If not specified, the default is false.
        replaceFromField: true
      to:
        # The target namespace
        namespace: identity
        # The target type in the target namespace
        type: User
        # The top-level field in the target namespace whose value is used to populate the linked field.
        field: user
        # The name of the argument for top-level field. At runtime, the value of the "fromField" in the source type
        # is passed.
        argument: id
        # (Optional) Whether or not the argument for the top-level field is nullable. If true, the query to the target namespace
        # is still made even if the value for "fromField" is null. Otherwise, the linked field's value is null.
        # If not specified, "nullable" is false.
        nullable: false
        # (Optional) The field in the target type whose value should be the same as the argument. If only this field
        # is requested in the query, the engine can omit the query to the target namespace and simply use the argument's
        # value for this field.
        # If not specified, no optimization is performed.
        variableField: userId
        # (Optional) Whether or not the linked field's type should be non-null.
        # If not specified, the default is false.
        targetNonNullable: true
```

## Linking List Field

If the linked field is a list type, the `fromField` should also have a list value to be passed as the argument. In the following example, `likeUserIds` is a list of IDs for the users who liked the page.
```graphql
type ContentQuery {
   page(id: String): Page
}

type Page {
    id: String
    title: String
    likeUserIds: [String]
}
```

The following link definition adds `likeUsers` field by querying `users` in `identity` namespace using `likeUserIds`.
```YAML
- namespace: Content
  links: 
    - from:
        type: Page
        field: likeUsers
        fromField: likeUserIds
        replaceFromField: true
      to:
        namespace: identity
        type: User
        field: users
        argument: ids
```

The resulting schema has `likeUsers` field added as a list of `User`.
```
type Query {
   page(id: String): Page
}

type Page {
    id: String
    title: String
    likeUsers: [User] # This is the linked field.
}

type User {
    userId: String
    name: String
}
```

## Complex Links
Complex link are very similar to simple links with only difference where they can contain multiple arguments and they are
passed to top level query field in target namespace as is. Complex link are created using `ComplexBuilder` instance that
can be obtained via `Link.newComplexBuilder()`. See `ComplexBuilder` for more details on API and YAML keys.

Consider there exist a `topBar` query in `bar` namespace that intakes multiple arguments to fetch `Bar` field as shown below:

```graphql
type Bar {
    topBar(id: String, site: String, baz: String): Bar
}
```
and if `Foo` type in `foo` namespace is defined as
```graphql
type Foo {
    id: String
    siteId: String
    name: String
}
```  
then, we can use complex link to add a field `bar(inputParm: String)` into `Foo` type in the final schema with YAML 
configuration as shown below:

```YAML
- namespace: foo
    complexLinks:
    - sourceType: Foo
      field: bar
      targetNamespace: bar
      targetType: Bar
      topLevelQueryField: topBar
      arguments:
        - sourceName: id # refer to `id` field in `Foo` type
          argumentSource: OBJECT_FIELD
          queryArgumentName: id # refer to `id` argument in `topBar` query in `Bar` type
        - sourceName: siteId
          argumentSource: OBJECT_FIELD
          queryArgumentName: site
        - sourceName: inputParam
          argumentSource: FIELD_ARGUMENT
          queryArgumentName: baz
```

NOTE: Complex links also support making the target field non-nullable. Simply add `targetNonNullable` to as top level 
field in map as showing below:

```YAML
- namespace: foo
    complexLinks:
    - sourceType: Foo
      field: bar
      targetNamespace: bar
      targetType: Bar 
      targetNonNullable: true #final schema will have `bar` as non-nullable field (i.e. bar: Bar!)
      ...
```
