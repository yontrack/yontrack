package net.nemerosa.ontrack.kdsl.acceptance.tests.ha

import net.nemerosa.ontrack.kdsl.acceptance.tests.support.uid
import net.nemerosa.ontrack.kdsl.acceptance.tests.support.waitUntil
import net.nemerosa.ontrack.kdsl.spec.extension.scm.AssistedChange
import net.nemerosa.ontrack.kdsl.spec.extension.scm.AssistedChangeBasis
import net.nemerosa.ontrack.kdsl.spec.extension.scm.MockScmRepositoryContext
import net.nemerosa.ontrack.kdsl.spec.extension.scm.assistedChange
import net.nemerosa.ontrack.kdsl.spec.extension.scm.mockScmBranchProperty
import net.nemerosa.ontrack.kdsl.spec.extension.scm.mockScmBuildCommitProperty
import net.nemerosa.ontrack.kdsl.spec.extension.scm.mockScmProjectProperty
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The assisted change of a build is computed through a queue, one computation at a time per build
 * whatever the replica (#2083): triggers handled by two replicas never interleave, and the build
 * ends with the value of its final state.
 *
 * The mock SCM keeps its repositories in the memory of each backend: the same commits are
 * registered on both, in the same order, which gives them the same ids.
 */
class ACCHAAssistedChange : AbstractACCHATestSupport() {

    @Test
    fun `a build created through A and its commit changed through B ends with the assisted change of its final commit`() {
        val repository = uid("acc-ha-assisted-")
        val repoA = MockScmRepositoryContext(ontrackA, repository)
        val repoB = MockScmRepositoryContext(ontrackB, repository)
        val (before, feature, fix) = listOf(
            "Before",
            "Some feature",
            """
                Some fix

                Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
            """.trimIndent(),
        ).map { message ->
            val id = repoA.repositoryCommit(message)
            assertEquals(id, repoB.repositoryCommit(message), "Same commit on both backends")
            id
        }

        val project = ontrackA.createProject(uid("p_"), "")
        project.mockScmProjectProperty = repository
        val branch = project.createBranch(uid("b_"), "")
        branch.mockScmBranchProperty = "main"
        val from = branch.createBuild(uid("from_"))
        from.mockScmBuildCommitProperty = before

        // Created through A, which computes its value for its first commit...
        val to = branch.createBuild(uid("to_"))
        to.mockScmBuildCommitProperty = feature
        // ... while B computes it for the next one
        val toOnB = assertNotNull(ontrackB.findBuildByName(project.name, branch.name, to.name), "Build read through B")
        toOnB.mockScmBuildCommitProperty = fix

        val expected = AssistedChange(
            basis = AssistedChangeBasis.COMPUTED,
            assistants = listOf("Claude Code"),
            assistedCommits = 1,
            totalCommits = 2,
            previousBuildId = from.id.toInt(),
        )
        waitUntil(timeout = 30_000L, interval = 500L, task = "Assisted change of the final commit") {
            to.assistedChange == expected
        }
        // A computation of the first commit finishing last would overwrite it
        Thread.sleep(3_000L)
        assertEquals(expected, to.assistedChange, "The assisted change is still the one of the final commit")
    }

}
