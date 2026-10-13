package net.nemerosa.ontrack.kdsl.acceptance.tests.ha

import net.nemerosa.ontrack.kdsl.acceptance.tests.ACCProperties
import net.nemerosa.ontrack.kdsl.acceptance.tests.support.uid
import net.nemerosa.ontrack.kdsl.acceptance.tests.support.waitUntil
import net.nemerosa.ontrack.kdsl.spec.search.search
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The search documents of a renamed project are re-indexed through a queue, one re-indexation at a
 * time per project whatever the replica (#2083): two renames handled by two replicas never
 * interleave, and the documents end with the last name only.
 */
class ACCHASearchRename : AbstractACCHATestSupport() {

    @Test
    fun `a project renamed through A then through B ends with search documents carrying the last name only`() {
        val project = ontrackA.createProject(uid("p"), "")
        val branch = project.createBranch(uid("b"), "")
        val first = uid("n")
        val last = uid("n")

        rename(ACCProperties.HA.A.url, project.id.toInt(), first)
        rename(ACCProperties.HA.B.url, project.id.toInt(), last)

        waitUntil(timeout = 30_000L, interval = 500L, task = "Branch documents re-indexed with the last name") {
            titles(last, branch.name) == listOf("$last/${branch.name}")
        }
        // A re-indexation for the first name finishing last would bring its documents back
        Thread.sleep(3_000L)
        assertEquals(listOf("$last/${branch.name}"), titles(last, branch.name), "Branch document with the last name")
        listOf(project.name, first).forEach { name ->
            assertEquals(
                listOf("$last/${branch.name}"),
                titles(name, branch.name),
                "No branch document with the former name $name"
            )
        }
    }

    /**
     * Titles of the documents of the branch found by searching for a name, or for the branch
     */
    private fun titles(query: String, branch: String): List<String> =
        (ontrack.search(query = query, types = listOf("branch"), size = 50).items +
                ontrack.search(query = branch, types = listOf("branch"), size = 50).items)
            .map { it.title }
            .filter { it.endsWith("/$branch") }
            .distinct()

    private fun rename(url: String, id: Int, name: String) {
        val errors = graphQL(
            url,
            """
                mutation {
                    updateProject(input: {id: $id, name: "$name"}) {
                        errors { message }
                    }
                }
            """
        ).path("data").path("updateProject").path("errors")
        assertTrue(errors.isEmpty(), "Project renamed to $name through $url: $errors")
    }

}
