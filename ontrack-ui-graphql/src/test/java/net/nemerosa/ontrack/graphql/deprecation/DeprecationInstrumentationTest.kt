package net.nemerosa.ontrack.graphql.deprecation

import graphql.GraphQL
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import net.nemerosa.ontrack.model.deprecation.DeprecationService
import net.nemerosa.ontrack.model.deprecation.DeprecationSurface
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class DeprecationInstrumentationTest {

    private data class Usage(val surface: DeprecationSurface, val item: String, val message: String)

    private val usages = mutableListOf<Usage>()

    private val deprecationService = object : DeprecationService {
        override fun deprecatedUsage(surface: DeprecationSurface, item: String, message: String) {
            usages += Usage(surface, item, message)
        }
    }

    private val graphQL: GraphQL = run {
        val sdl = """
            type Query {
                items: [Item!]!
                search(token: String, text: String @deprecated(reason: "Removed in V6. Use token instead. See #1")): String
            }
            type Item {
                name: String
                oldName: String @deprecated(reason: "Removed in V6. Use name instead. See #2")
            }
        """
        val wiring = RuntimeWiring.newRuntimeWiring()
            .type("Query") { type ->
                type.dataFetcher("items") { listOf(mapOf("name" to "a"), mapOf("name" to "b")) }
                    .dataFetcher("search") { "result" }
            }
            .build()
        val schema = SchemaGenerator().makeExecutableSchema(SchemaParser().parse(sdl), wiring)
        GraphQL.newGraphQL(schema)
            .instrumentation(DeprecationInstrumentation(deprecationService))
            .build()
    }

    private fun execute(query: String) {
        val result = graphQL.execute(query)
        assertEquals(emptyList(), result.errors)
    }

    @Test
    fun `No usage when no deprecated field is queried`() {
        execute("{ items { name } search(token: \"x\") }")
        assertEquals(emptyList(), usages)
    }

    @Test
    fun `Every fetch of a deprecated field is reported, including trivial ones`() {
        execute("{ items { name oldName } }")
        assertEquals(
            listOf(
                Usage(DeprecationSurface.GRAPHQL, "Item.oldName", "Removed in V6. Use name instead. See #2"),
                Usage(DeprecationSurface.GRAPHQL, "Item.oldName", "Removed in V6. Use name instead. See #2"),
            ),
            usages
        )
    }

    @Test
    fun `A deprecated argument is reported only when given`() {
        execute("{ search(text: \"x\") }")
        assertEquals(
            listOf(
                Usage(DeprecationSurface.GRAPHQL, "Query.search(text)", "Removed in V6. Use token instead. See #1"),
            ),
            usages
        )
    }
}
