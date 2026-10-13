package net.nemerosa.ontrack.graphql.prefetch

import graphql.GraphQL
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import io.mockk.mockk
import io.mockk.verify
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.NameDescription
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.PropertyService
import net.nemerosa.ontrack.model.support.ReadMemo
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PropertiesPrefetchInstrumentationTest {

    private val propertyService = mockk<PropertyService>(relaxed = true)

    private val projects = (1..3).map { Project.of(NameDescription.nd("p$it", "")).withId(ID.of(it)) }

    private val graphQL: GraphQL = run {
        val sdl = """
            type Query {
                projects: [Project!]!
                project: Project
                names: [String!]!
            }
            type Project {
                name: String
            }
        """
        val wiring = RuntimeWiring.newRuntimeWiring()
            .type("Query") { type ->
                type.dataFetcher("projects") { projects }
                    .dataFetcher("project") { projects.first() }
                    .dataFetcher("names") { listOf("a", "b") }
            }
            .build()
        val schema = SchemaGenerator().makeExecutableSchema(SchemaParser().parse(sdl), wiring)
        GraphQL.newGraphQL(schema)
            .instrumentation(PropertiesPrefetchInstrumentation(propertyService))
            .build()
    }

    private fun execute(query: String) {
        val result = graphQL.execute(query)
        assertEquals(emptyList(), result.errors)
    }

    @Test
    fun `The entities of a list are prefetched in a unit of work`() {
        ReadMemo.unitOfWork {
            execute("{ projects { name } }")
        }
        verify(exactly = 1) { propertyService.prefetchProperties(projects) }
    }

    @Test
    fun `Nothing is prefetched outside of a unit of work`() {
        execute("{ projects { name } }")
        verify(exactly = 0) { propertyService.prefetchProperties(any()) }
    }

    @Test
    fun `Nothing is prefetched for a single entity, nor for a list of values`() {
        ReadMemo.unitOfWork {
            execute("{ project { name } names }")
        }
        verify(exactly = 0) { propertyService.prefetchProperties(any()) }
    }
}
