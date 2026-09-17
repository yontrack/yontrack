package net.nemerosa.ontrack.graphql.schema

import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class GQLRootQueryPaginatedProjectsIT : AbstractQLKTITSupport() {

    @Test
    @AsAdminTest
    fun `Paginated list of projects`() {
        // Removing all projects
        structureService.projectList.forEach { project -> structureService.deleteProject(project.id) }
        // Creating projects
        repeat(15) { no ->
            val name = "P${no.toString().padStart(2, '0')}"
            project(name = name)
        }
        // Querying with an offset
        run(
            """
                {
                    paginatedProjects(offset: 10) {
                        pageItems {
                            name
                        }
                    }
                }
            """.trimIndent()
        ) { data ->
            assertEquals(5, data["paginatedProjects"]["pageItems"].size())
            assertEquals("P10", data["paginatedProjects"]["pageItems"][0]["name"].asText())
        }
    }

    @Test
    @AsAdminTest
    fun `Filtering projects by name`() {
        // Removing all projects
        structureService.projectList.forEach { project -> structureService.deleteProject(project.id) }
        // Creating projects
        project(name = "Project A")
        project(name = "Project B")
        project(name = "Other")
        // Querying with a name filter
        run(
            """
                {
                    paginatedProjects(name: "Project") {
                        pageItems {
                            name
                        }
                    }
                }
            """.trimIndent()
        ) { data ->
            val items = data["paginatedProjects"]["pageItems"]
            assertEquals(2, items.size())
            val names = items.map { it["name"].asText() }.toSet()
            assertEquals(setOf("Project A", "Project B"), names)
        }
    }

    @Test
    @AsAdminTest
    fun `Filtering projects by one label`() {
        val label = label()
        val other = label()
        val withLabel = project { labels = listOf(label) }
        project { labels = listOf(other) }
        project()
        assertPaginatedProjectNames(
            labels = listOf(label.getDisplay()),
            expected = setOf(withLabel.name),
        )
    }

    @Test
    @AsAdminTest
    fun `Filtering projects by two labels combines them with AND`() {
        val team = label()
        val language = label()
        val both = project { labels = listOf(team, language) }
        project { labels = listOf(team) }
        project { labels = listOf(language) }
        assertPaginatedProjectNames(
            labels = listOf(team.getDisplay(), language.getDisplay()),
            expected = setOf(both.name),
        )
    }

    @Test
    @AsAdminTest
    fun `Filtering projects by label and by name combines them with AND`() {
        val label = label()
        val prefix = uid("PFX")
        val matching = project(name = "$prefix-matching") { labels = listOf(label) }
        project(name = "$prefix-no-label")
        project(name = uid("OTHER")) { labels = listOf(label) }
        assertPaginatedProjectNames(
            name = prefix,
            labels = listOf(label.getDisplay()),
            expected = setOf(matching.name),
        )
    }

    @Test
    fun `Filtering projects by label does not return the projects the user cannot see`() {
        val label = asAdmin { label() }
        val visible = asAdmin { project { labels = listOf(label) } }
        asAdmin { project { labels = listOf(label) } }
        withNoGrantViewToAll {
            asUserWithView(visible) {
                assertPaginatedProjectNames(
                    labels = listOf(label.getDisplay()),
                    expected = setOf(visible.name),
                )
            }
        }
    }

    /**
     * Runs `paginatedProjects` with the given filters and checks both the page items and the
     * total size - the total is what tells the filtering apart from a filtering of the page
     * which has already been extracted.
     */
    private fun assertPaginatedProjectNames(
        name: String? = null,
        labels: List<String>,
        expected: Set<String>,
    ) {
        run(
            """
                query PaginatedProjects(${'$'}name: String, ${'$'}labels: [String!]) {
                    paginatedProjects(name: ${'$'}name, labels: ${'$'}labels) {
                        pageInfo {
                            totalSize
                        }
                        pageItems {
                            name
                        }
                    }
                }
            """,
            mapOf("name" to name, "labels" to labels)
        ) { data ->
            val projects = data["paginatedProjects"]
            assertEquals(expected, projects["pageItems"].map { it["name"].asText() }.toSet())
            assertEquals(expected.size, projects["pageInfo"]["totalSize"].asInt())
        }
    }

}