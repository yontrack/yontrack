package net.nemerosa.ontrack.kdsl.acceptance.tests.kubernetes

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The `kubernetes` auto-versioning post-processing (#2171) against a real cluster (#2172).
 */
class ACCKubernetesPostProcessing : AbstractACCKubernetesTestSupport() {

    @Test
    fun `The post-processing runs in a Job and its result is pushed to the upgrade branch`() {
        runPostProcessing(
            dockerCommand = "echo \"version=\$VERSION token=\$PP_TOKEN\" > post-processing.txt",
            expectedState = "PR_MERGED",
        ) { outcome ->
            val upgradeBranch = outcome.repository.branchStartingWith(UPGRADE_BRANCH_2_0_0)
            outcome.repository.withBranch(upgradeBranch) { clone ->
                val gradle = assertNotNull(clone.file("gradle.properties"))
                assertTrue("some-version = 2.0.0" in gradle, "The version was changed: $gradle")
                val written = assertNotNull(clone.file("post-processing.txt"), "The Job wrote its file")
                assertTrue("version=2.0.0" in written, "The version reached the command: $written")
                assertTrue("token=$ALLOWED_SECRET_VALUE" in written, "The Secret reached the command: $written")
                // Source commit, version change, post-processing
                assertEquals(3, clone.commits.size, "Commits: ${clone.commits}")
                assertEquals(listOf("post-processing.txt"), clone.lastCommitFiles)
            }
        }
    }

    @Test
    fun `A failing post-processing is reported with its step, exit code and logs and pushes nothing`() {
        runPostProcessing(
            dockerCommand = "echo about-to-fail; exit 3",
            expectedState = "ERROR",
        ) { outcome ->
            val message = outcome.entry.mostRecentState.data["message"]?.asText()
            assertNotNull(message, "The error has a message")
            assertTrue("failed in the `run` step with exit code 3" in message, "Step and exit code: $message")
            assertTrue("about-to-fail" in message, "The tail of the logs: $message")
            val upgradeBranch = outcome.repository.branchStartingWith(UPGRADE_BRANCH_2_0_0)
            outcome.repository.withBranch(upgradeBranch) { clone ->
                assertTrue("some-version = 2.0.0" in (clone.file("gradle.properties") ?: ""), "The version was changed")
                assertNull(clone.file("post-processing.txt"), "Nothing was pushed")
                // Source commit and version change only
                assertEquals(2, clone.commits.size, "Commits: ${clone.commits}")
            }
        }
    }
}
