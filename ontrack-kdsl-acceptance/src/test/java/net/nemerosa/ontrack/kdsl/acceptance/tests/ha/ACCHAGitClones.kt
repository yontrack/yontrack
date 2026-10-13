package net.nemerosa.ontrack.kdsl.acceptance.tests.ha

import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.kdsl.acceptance.tests.ACCProperties
import net.nemerosa.ontrack.kdsl.acceptance.tests.support.uid
import net.nemerosa.ontrack.kdsl.acceptance.tests.support.waitUntil
import net.nemerosa.ontrack.kdsl.spec.Branch
import net.nemerosa.ontrack.kdsl.spec.Ontrack
import net.nemerosa.ontrack.kdsl.spec.Project
import net.nemerosa.ontrack.kdsl.spec.configurations.configurations
import net.nemerosa.ontrack.kdsl.spec.extension.git.GitBranchConfigurationProperty
import net.nemerosa.ontrack.kdsl.spec.extension.git.gitBranchConfigurationProperty
import net.nemerosa.ontrack.kdsl.spec.extension.gitlab.GitLabConfiguration
import net.nemerosa.ontrack.kdsl.spec.extension.gitlab.GitLabProjectConfigurationProperty
import net.nemerosa.ontrack.kdsl.spec.extension.gitlab.gitLab
import net.nemerosa.ontrack.kdsl.spec.extension.gitlab.gitLabConfigurationProperty
import net.nemerosa.ontrack.kdsl.spec.jobs.JobStatus
import net.nemerosa.ontrack.kdsl.spec.jobs.jobs
import net.nemerosa.ontrack.kdsl.spec.support.ServiceConfiguration
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
 * The remotes are bare repositories this test writes in the directory both backends mount as
 * `/git-remote`, reached through a GitLab configuration whose URL is `file:///git-remote`: no
 * GitLab API is called, only the clones.
 */
class ACCHAGitClones : AbstractACCHATestSupport() {

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
    // Projects and jobs
    // ============================================================================================

    /**
     * A project whose GitLab configuration points at the remote, never indexed on its own.
     */
    private fun gitProject(remote: Remote): Project {
        val configuration = uid("gl_")
        ontrack.configurations.gitLab.create(
            GitLabConfiguration(
                name = configuration,
                url = "file://$REMOTES",
                token = "not-used",
            )
        )
        return ontrack.createProject(uid("p_"), "").apply {
            gitLabConfigurationProperty = GitLabProjectConfigurationProperty(
                configuration = configuration,
                repository = remote.name,
                indexationInterval = 0,
            )
        }
    }

    /**
     * Reconciles the jobs of the cluster with the projects, through the orchestrator, until [job]
     * is found.
     */
    private fun refreshJobs(job: () -> JobStatus?) {
        val orchestrator = ontrack.jobs.status("core", "orchestrator", "collection")
            ?: fail("No orchestrator job")
        waitUntil(timeout = 60_000L, interval = 1_000L, task = "The jobs of the project are created") {
            job() != null || run {
                ontrack.jobs.runNow(orchestrator.id)
                false
            }
        }
    }

    private fun indexationJob(remote: Remote): JobStatus =
        findIndexationJob(remote) ?: fail("No indexation job for ${remote.url}")

    private fun findIndexationJob(remote: Remote): JobStatus? =
        ontrack.jobs.list("git", "git-indexation")
            .firstOrNull { it.description.startsWith("${remote.url} (") }

    private fun buildSyncJob(branch: Branch): JobStatus? =
        ontrack.jobs.list("git", "git-build-sync").firstOrNull { it.key == branch.id.toString() }

    /**
     * Runs the indexation of the project now, and waits for it to be over.
     */
    private fun index(project: Project, remote: Remote) {
        val runs = indexationJob(remote).runCount
        waitUntil(timeout = 30_000L, interval = 500L, task = "The indexation is launched") {
            projectSync(ontrack, project, reset = false)
        }
        waitForIndexation(remote, runs)
    }

    private fun waitForIndexation(remote: Remote, runs: Int) {
        waitUntil(timeout = 60_000L, interval = 500L, task = "The indexation is over") {
            indexationJob(remote).let { it.runCount > runs && !it.running }
        }
        assertNull(indexationJob(remote).lastError, "The indexation succeeded")
    }

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

    // ============================================================================================
    // REST API of the Git extension
    // ============================================================================================

    private fun projectSync(backend: Ontrack, project: Project, reset: Boolean): Boolean =
        backend.connector.post(
            "extension/git/project-sync/${project.id}",
            // The name of the field, as the request has always read it
            body = mapOf("isReset" to reset).asJson(),
        ).body.asJson().path("success").asBoolean()

    private fun syncInfo(backend: Ontrack, project: Project): JsonNode =
        backend.connector.get("extension/git/project-sync/${project.id}").body.asJson()

    private fun JsonNode.branches(): List<String> = path("branches").values().map { it.path("name").asString() }

    // ============================================================================================
    // Remotes
    // ============================================================================================

    /**
     * A bare repository in the directory the backends mount, and a working copy to push to it, both
     * deleted at the end.
     */
    private fun withRemote(code: (Remote) -> Unit) {
        val name = uid("repo-")
        val bare = File(ACCProperties.HA.Git.dir, "$name.git")
        val work = Files.createTempDirectory("acc-ha-git").toFile()
        try {
            git(bare.parentFile.apply { mkdirs() }, "init", "--bare", "--initial-branch=main", bare.absolutePath)
            git(work, "init", "--initial-branch=main")
            git(work, "config", "user.name", "ACC")
            git(work, "config", "user.email", "acc@ontrack.local")
            git(work, "remote", "add", "origin", bare.absolutePath)
            code(Remote(name, work))
        } finally {
            work.deleteRecursively()
            bare.deleteRecursively()
        }
    }

    /**
     * @property name Name of the repository, the GitLab repository of the project
     * @property work Working copy
     */
    private class Remote(val name: String, val work: File) {

        /** Remote of the repository as the backends clone it */
        val url = "file://$REMOTES/$name.git"

        fun commit(message: String) {
            git(work, "commit", "--allow-empty", "-m", message)
        }

        fun tag(name: String) {
            git(work, "tag", name)
        }

        fun branch(name: String) {
            git(work, "checkout", "-b", name)
        }

        fun push(branch: String = "main") {
            git(work, "push", "--tags", "origin", branch)
        }

        fun deleteRemoteBranch(branch: String) {
            git(work, "push", "origin", "--delete", branch)
        }
    }

    companion object {

        /** Where the backends mount the remotes */
        private const val REMOTES = "/git-remote"

        /** Freshness window of the clones in the stack, 5 seconds, and a margin */
        private const val FRESHNESS_MS = 6_000L

        private fun git(dir: File, vararg args: String) {
            val process = ProcessBuilder(listOf("git") + args.toList())
                .directory(dir)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            check(process.waitFor() == 0) { "git ${args.joinToString(" ")} failed: $output" }
        }
    }
}
