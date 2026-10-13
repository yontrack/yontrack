package net.nemerosa.ontrack.kdsl.acceptance.tests.ha

import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.kdsl.acceptance.tests.support.waitUntil
import net.nemerosa.ontrack.kdsl.spec.Branch
import net.nemerosa.ontrack.kdsl.spec.Ontrack
import net.nemerosa.ontrack.kdsl.spec.Project
import net.nemerosa.ontrack.kdsl.spec.extension.git.GitBranchConfigurationProperty
import net.nemerosa.ontrack.kdsl.spec.extension.git.gitBranchConfigurationProperty
import net.nemerosa.ontrack.kdsl.spec.jobs.JobStatus
import net.nemerosa.ontrack.kdsl.spec.jobs.jobs
import net.nemerosa.ontrack.kdsl.spec.support.ServiceConfiguration
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Each backend has Git clones of its own, which stay fresh and can be reset whichever backend does
 * the work (#2085):
 *
 * - a read from a clone fetches first when the clone is older than the freshness window, 5 seconds
 *   in this stack;
 * - a reset bumps the generation of the repository, and every backend re-clones on its next use;
 * - the synchronisation status of a project is the shared status of its indexation job.
 *
 * See [AbstractACCHAGitTestSupport] for the remotes.
 */
class ACCHAGitClones : AbstractACCHAGitTestSupport() {

    @Test
    fun `a tag pushed after the last indexation is seen by the tag-based commit link through either backend`() {
        withRemote { remote ->
            remote.commit("First")
            remote.tag("1.0.0")
            remote.push()

            val project = gitProject(remote)
            val branch = project.createBranch("main", "")
            // Builds named after the tags, synchronised once a week: only when launched
            branch.gitBranchConfigurationProperty = GitBranchConfigurationProperty(
                branch = "main",
                buildCommitLink = ServiceConfiguration(id = "tag", data = null),
                buildTagInterval = 7 * 24 * 60,
            )
            refreshJobs { buildSyncJob(branch) }
            index(project, remote)

            buildSync(ontrackA, branch, "1.0.0")

            // Each tag after the last indexation, seen once the clone of the backend running the
            // build synchronisation is older than the freshness window, whichever backend it is
            listOf(ontrackB to "1.1.0", ontrackA to "1.2.0", ontrackB to "1.3.0").forEach { (backend, tag) ->
                remote.commit("Commit for $tag")
                remote.tag(tag)
                remote.push()
                TimeUnit.MILLISECONDS.sleep(FRESHNESS_MS)
                buildSync(backend, branch, tag)
            }
        }
    }

    @Test
    fun `a reset through A makes B re-clone on its next use`() {
        withRemote { remote ->
            remote.commit("First")
            remote.push()
            remote.branch("feature")
            remote.commit("Feature")
            remote.push("feature")

            val project = gitProject(remote)
            refreshJobs { findIndexationJob(remote) }
            index(project, remote)

            assertEquals(listOf("feature", "main"), syncInfo(ontrackB, project).branches(), "B clones the repository")

            // A branch deleted on the remote stays in a clone which only fetches
            remote.deleteRemoteBranch("feature")
            TimeUnit.MILLISECONDS.sleep(FRESHNESS_MS)
            assertEquals(
                listOf("feature", "main"),
                syncInfo(ontrackB, project).branches(),
                "A fetch keeps the branches deleted on the remote"
            )

            // Reset through A, which runs the indexation job now
            val runs = indexationJob(remote).runCount
            assertTrue(projectSync(ontrackA, project, reset = true), "Reset through A")
            waitForIndexation(remote, runs)

            assertEquals(listOf("main"), syncInfo(ontrackB, project).branches(), "B re-clones on its next use")
            assertEquals(listOf("main"), syncInfo(ontrackA, project).branches(), "A re-clones on its next use")
        }
    }

    @Test
    fun `the synchronisation status of a project is the same through A and B`() {
        withRemote { remote ->
            remote.commit("First")
            remote.push()

            val project = gitProject(remote)
            refreshJobs { findIndexationJob(remote) }

            // Never indexed
            val neverA = syncInfo(ontrackA, project)
            assertEquals("NONE", neverA.path("status").asString(), "Never indexed")
            assertEquals(neverA, syncInfo(ontrackB, project), "Same status through A and B before the indexation")

            index(project, remote)

            val throughA = syncInfo(ontrackA, project)
            val throughB = syncInfo(ontrackB, project)
            assertEquals("IDLE", throughA.path("status").asString(), "Indexed")
            assertEquals(listOf("main"), throughA.branches(), "Branches of the repository")
            assertEquals(throughA, throughB, "Same status through A and B")
            assertTrue(throughA.path("lastRunDate").isString, "Last run of the indexation")
            assertTrue(throughA.path("lastError").isNull, "No error")
        }
    }

    // ============================================================================================
    // Jobs and REST API of the Git extension
    // ============================================================================================

    private fun buildSyncJob(branch: Branch): JobStatus? =
        ontrack.jobs.list("git", "git-build-sync").firstOrNull { it.key == branch.id.toString() }

    /**
     * Launches the build synchronisation of the branch through a backend, until the build named
     * after the tag is created. The job runs on either backend.
     */
    private fun buildSync(backend: Ontrack, branch: Branch, tag: String) {
        waitUntil(
            timeout = 60_000L,
            interval = 1_000L,
            task = "Build $tag created from its tag",
            onTimeout = { fail("Build $tag not created from its tag, the build synchronisation is ${buildSyncJob(branch)}") },
        ) {
            if (ontrack.findBuildByName(branch.project.name, branch.name, tag) != null) {
                true
            } else {
                // Launched again until the build is there: the job may already be running
                backend.connector.post("extension/git/sync/${branch.id}")
                false
            }
        }
    }

    private fun syncInfo(backend: Ontrack, project: Project): JsonNode =
        backend.connector.get("extension/git/project-sync/${project.id}").body.asJson()

    private fun JsonNode.branches(): List<String> = path("branches").values().map { it.path("name").asString() }

    companion object {

        /** Freshness window of the clones in the stack, 5 seconds, and a margin */
        private const val FRESHNESS_MS = 6_000L
    }
}
