package net.nemerosa.ontrack.graphql.support

import graphql.ExecutionInput
import graphql.GraphQL
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parseAsJson
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * The `Long` scalar, as an output: a JSON number, like `Int`.
 */
class GQLScalarLongTest {

    private fun output(value: Long): String {
        val schema = GraphQLSchema.newSchema()
            .query(
                GraphQLObjectType.newObject()
                    .name("Query")
                    .field(
                        GraphQLFieldDefinition.newFieldDefinition()
                            .name("size")
                            .type(GQLScalarLong.INSTANCE)
                            .dataFetcher { value }
                    )
                    .build()
            )
            .build()
        val result = GraphQL.newGraphQL(schema).build().execute(ExecutionInput.newExecutionInput("{ size }").build())
        assertEquals(emptyList(), result.errors)
        return result.getData<Map<String, Any?>>().asJson().toString()
    }

    @Test
    fun `A long is output as a JSON number`() {
        assertEquals("""{"size":52428800}""", output(52_428_800L))
    }

    @Test
    fun `A long beyond the range of an int is output as a JSON number`() {
        assertEquals("""{"size":9007199254740991}""", output(9_007_199_254_740_991L))
        assertEquals(9_007_199_254_740_991L, output(9_007_199_254_740_991L).parseAsJson().path("size").asLong())
    }
}
