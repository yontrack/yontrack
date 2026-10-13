package net.nemerosa.ontrack.kdsl.acceptance.tests.ha

import net.nemerosa.ontrack.kdsl.acceptance.tests.ACCProperties
import net.nemerosa.ontrack.kdsl.spec.Project
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A project whose remote no longer exists is disabled once its indexation has failed more than
 * `ontrack.config.extension.git.remote.max-no-remote` times - 3 by default - in the cluster,
 * whichever backend runs the indexation (#2088): the count of the failures is shared.
 *
 * The indexation job runs on any backend: to know which one, the other one is down. Two failures
 * on A, then two on B: counted per backend, the project would stay enabled.
 */
class ACCHAGitNoRemote : AbstractACCHAGitTestSupport() {

    @Test
    fun `the missing-remote threshold counts the failures of all the backends`() {
        withRemote { remote ->
            remote.commit("First")
            remote.push()

            val project = gitProject(remote)
            refreshJobs { findIndexationJob(remote) }
            index(project, remote)

            remote.delete()

            // Two of the accepted failures, on A
            withBackendDown(B) {
                repeat(2) { index ->
                    runIndexation(project, remote, through = ontrackA)
                    assertFalse(isDisabled(ACCProperties.HA.A.url, project), "Project still enabled after ${index + 1} failure(s)")
                }
            }

            // The last accepted failure, and one more, on B
            withBackendDown(A) {
                runIndexation(project, remote, through = ontrackB)
                assertFalse(isDisabled(ACCProperties.HA.B.url, project), "Project still enabled after 3 failures")
                runIndexation(project, remote, through = ontrackB)
                assertTrue(isDisabled(ACCProperties.HA.B.url, project), "Project disabled after 4 failures")
            }
        }
    }

    private fun isDisabled(url: String, project: Project): Boolean =
        graphQL(url, "{ project(id: ${project.id}) { disabled } }")
            .path("data").path("project").path("disabled").asBoolean()
}
