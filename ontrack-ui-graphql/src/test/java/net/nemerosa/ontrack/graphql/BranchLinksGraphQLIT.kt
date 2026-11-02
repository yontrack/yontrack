package net.nemerosa.ontrack.graphql

import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.structure.Branch
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import kotlin.test.assertEquals

/**
 * The two sides of the links of a branch, and the deprecated names of these fields, whose
 * meaning must never change: `downstreamLinks` are the dependencies, `upstreamLinks` the consumers.
 */
@AsAdminTest
class BranchLinksGraphQLIT : AbstractQLKTITSupport() {

    @Test
    fun `The dependency links of a branch are the branches it depends on`() {
        withLinks { app, library ->
            assertEquals(
                listOf(library.id()),
                linkedBranches(app, "dependencyLinks"),
            )
            assertEquals(
                emptyList<Int>(),
                linkedBranches(library, "dependencyLinks"),
            )
        }
    }

    @Test
    fun `The consumer links of a branch are the branches depending on it`() {
        withLinks { app, library ->
            assertEquals(
                listOf(app.id()),
                linkedBranches(library, "consumerLinks"),
            )
            assertEquals(
                emptyList<Int>(),
                linkedBranches(app, "consumerLinks"),
            )
        }
    }

    @Test
    fun `The deprecated names keep their meaning`() {
        withLinks { app, library ->
            assertEquals(listOf(library.id()), linkedBranches(app, "downstreamLinks"))
            assertEquals(emptyList<Int>(), linkedBranches(library, "downstreamLinks"))
            assertEquals(listOf(app.id()), linkedBranches(library, "upstreamLinks"))
            assertEquals(emptyList<Int>(), linkedBranches(app, "upstreamLinks"))
        }
    }

    @Test
    fun `The new fields return what the deprecated ones do`() {
        withLinks { app, library ->
            listOf(app, library).forEach { branch ->
                assertEquals(links(branch, "downstreamLinks"), links(branch, "dependencyLinks"))
                assertEquals(links(branch, "upstreamLinks"), links(branch, "consumerLinks"))
            }
        }
    }

    @Test
    fun `The number of builds is an argument of the new fields`() {
        val oldLibrary = project<Branch> { branch() }
        val recentLibrary = project<Branch> { branch() }
        val app = project<Branch> { branch() }
        app.build().linkTo(oldLibrary.build())
        app.build().linkTo(recentLibrary.build())
        assertEquals(
            listOf(recentLibrary.id()),
            linkedBranches(app, "dependencyLinks(builds: 1)", "dependencyLinks"),
        )
        assertEquals(
            setOf(oldLibrary.id(), recentLibrary.id()),
            linkedBranches(app, "dependencyLinks").toSet(),
        )
    }

    private fun withLinks(code: (app: Branch, library: Branch) -> Unit) {
        val library = project<Branch> { branch() }
        val app = project<Branch> { branch() }
        app.build().linkTo(library.build())
        code(app, library)
    }

    private fun linkedBranches(branch: Branch, selection: String, responseKey: String = selection): List<Int> =
        links(branch, selection, responseKey).map { it.path("branch").path("id").asInt() }

    private fun links(branch: Branch, selection: String, responseKey: String = selection): List<JsonNode> =
        run(
            """
                {
                    branch(id: ${branch.id}) {
                        $selection {
                            qualifier
                            branch { id }
                            sourceBuild { id }
                            targetBuild { id }
                        }
                    }
                }
            """
        ).path("branch").path(responseKey).toList()

}
