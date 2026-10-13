package net.nemerosa.ontrack.kdsl.acceptance.tests.ha

import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.kdsl.acceptance.tests.ACCProperties
import net.nemerosa.ontrack.kdsl.acceptance.tests.support.uid
import net.nemerosa.ontrack.kdsl.acceptance.tests.support.waitUntil
import net.nemerosa.ontrack.kdsl.spec.Ontrack
import net.nemerosa.ontrack.kdsl.spec.Project
import net.nemerosa.ontrack.kdsl.spec.configurations.configurations
import net.nemerosa.ontrack.kdsl.spec.extension.gitlab.GitLabConfiguration
import net.nemerosa.ontrack.kdsl.spec.extension.gitlab.GitLabProjectConfigurationProperty
import net.nemerosa.ontrack.kdsl.spec.extension.gitlab.gitLab
import net.nemerosa.ontrack.kdsl.spec.extension.gitlab.gitLabConfigurationProperty
import net.nemerosa.ontrack.kdsl.spec.jobs.JobStatus
import net.nemerosa.ontrack.kdsl.spec.jobs.jobs
import java.io.File
import java.nio.file.Files
import kotlin.test.assertNull
import kotlin.test.fail

/**
 * Cross-replica tests on Git repositories (#2085, #2088).
 *
 * The remotes are bare repositories the tests write in the directory both backends mount as
 * `/git-remote`, reached through a GitLab configuration whose URL is `file:///git-remote`: no
 * GitLab API is called, only the clones.
 */
abstract class AbstractACCHAGitTestSupport : AbstractACCHATestSupport() {

    // ============================================================================================
    // Projects and jobs
    // ============================================================================================

    /**
     * A project whose GitLab configuration points at the remote, never indexed on its own.
     */
    protected fun gitProject(remote: Remote): Project {
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
    protected fun refreshJobs(job: () -> JobStatus?) {
        val orchestrator = ontrack.jobs.status("core", "orchestrator", "collection")
            ?: fail("No orchestrator job")
        waitUntil(timeout = 60_000L, interval = 1_000L, task = "The jobs of the project are created") {
            job() != null || run {
                ontrack.jobs.runNow(orchestrator.id)
                false
            }
        }
    }

    /**
     * @param via Backend to read the status through, the proxy by default
     */
    protected fun indexationJob(remote: Remote, via: Ontrack = ontrack): JobStatus =
        findIndexationJob(remote, via) ?: fail("No indexation job for ${remote.url}")

    protected fun findIndexationJob(remote: Remote, via: Ontrack = ontrack): JobStatus? =
        via.jobs.list("git", "git-indexation")
            .firstOrNull { it.description.startsWith("${remote.url} (") }

    /**
     * Runs the indexation of the project now, through a backend, and waits for it to be over. The
     * job runs on either backend, if both are up.
     */
    protected fun runIndexation(project: Project, remote: Remote, through: Ontrack = ontrack) {
        val runs = indexationJob(remote, through).runCount
        waitUntil(timeout = 30_000L, interval = 500L, task = "The indexation is launched") {
            projectSync(through, project, reset = false)
        }
        waitForIndexationRun(remote, runs, through)
    }

    /**
     * Runs the indexation of the project now, and waits for it to succeed.
     */
    protected fun index(project: Project, remote: Remote) {
        runIndexation(project, remote)
        assertNull(indexationJob(remote).lastError, "The indexation succeeded")
    }

    /**
     * Waits for a run of the indexation after [runs] runs to be over.
     */
    protected fun waitForIndexationRun(remote: Remote, runs: Int, via: Ontrack = ontrack) {
        waitUntil(timeout = 60_000L, interval = 500L, task = "The indexation is over") {
            indexationJob(remote, via).let { it.runCount > runs && !it.running }
        }
    }

    protected fun waitForIndexation(remote: Remote, runs: Int) {
        waitForIndexationRun(remote, runs)
        assertNull(indexationJob(remote).lastError, "The indexation succeeded")
    }

    // ============================================================================================
    // REST API of the Git extension
    // ============================================================================================

    protected fun projectSync(backend: Ontrack, project: Project, reset: Boolean): Boolean =
        backend.connector.post(
            "extension/git/project-sync/${project.id}",
            // The name of the field, as the request has always read it
            body = mapOf("isReset" to reset).asJson(),
        ).body.asJson().path("success").asBoolean()

    // ============================================================================================
    // Remotes
    // ============================================================================================

    /**
     * A bare repository in the directory the backends mount, and a working copy to push to it, both
     * deleted at the end.
     */
    protected fun withRemote(code: (Remote) -> Unit) {
        val name = uid("repo-")
        val bare = File(ACCProperties.HA.Git.dir, "$name.git")
        val work = Files.createTempDirectory("acc-ha-git").toFile()
        try {
            git(bare.parentFile.apply { mkdirs() }, "init", "--bare", "--initial-branch=main", bare.absolutePath)
            git(work, "init", "--initial-branch=main")
            git(work, "config", "user.name", "ACC")
            git(work, "config", "user.email", "acc@ontrack.local")
            git(work, "remote", "add", "origin", bare.absolutePath)
            code(Remote(name, work, bare))
        } finally {
            work.deleteRecursively()
            bare.deleteRecursively()
        }
    }

    /**
     * @property name Name of the repository, the GitLab repository of the project
     * @property work Working copy
     * @property bare The bare repository, the remote
     */
    protected class Remote(val name: String, val work: File, private val bare: File) {

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

        /**
         * Deletes the remote: the backends no longer find it.
         */
        fun delete() {
            check(bare.deleteRecursively()) { "Cannot delete $bare" }
        }
    }

    companion object {

        /** Where the backends mount the remotes */
        private const val REMOTES = "/git-remote"

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
