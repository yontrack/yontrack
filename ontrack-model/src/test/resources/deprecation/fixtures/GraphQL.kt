package fixture

class GQLTypeFixture {
    fun createType() = GraphQLObjectType.newObject()
        .name("Fixture")
        .field {
            it.name("oldField")
                .type(GraphQLString)
                .deprecate("Removed in V7. Use newField instead. See #1234")
        }
        .field(HookResponse::info, deprecation = "Will be removed one day")
        .build()

    fun createQuery() = GraphQLFieldDefinition.newFieldDefinition()
        .name("fixture")
        .argument(
            GraphQLArgument.newArgument()
                .name(ARG_TOKEN)
                .type(GraphQLString)
                .deprecate("Removed in V7. Use query instead. See #1234")
        )
        .build()

    companion object {
        private const val ARG_TOKEN = "token"
    }
}
