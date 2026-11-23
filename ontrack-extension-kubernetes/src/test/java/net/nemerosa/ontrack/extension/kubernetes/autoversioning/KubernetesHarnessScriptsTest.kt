package net.nemerosa.ontrack.extension.kubernetes.autoversioning

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Runs the harness scripts for real, with the local `sh` and `git`, against a local bare
 * repository: what the `clone` and `push` steps of a Job do, without a cluster.
 */
class KubernetesHarnessScriptsTest {

    @TempDir
    lateinit var dir: File

    private val branch = "feature/auto-upgrade-dep-2.0.0"
    private lateinit var remote: File
    private lateinit var workTree: File
    private lateinit var gitDir: File

    @BeforeEach
    fun before() {
        assumeTrue(available("git", "--version") && available("sh", "-c", "true"), "git and sh are needed")
        remote = File(dir, "remote.git")
        workTree = File(dir, "workspace")
        gitDir = File(dir, "git/repo.git")
        File(dir, "git").mkdirs()
        File(dir, "home").mkdirs()
        File(dir, "credentials").mkdirs()
        // Remote with the upgrade branch
        val seed = File(dir, "seed").apply { mkdirs() }
        git(dir, "init", "--quiet", "--bare", remote.path)
        git(seed, "init", "--quiet")
        File(seed, "version.properties").writeText("version = 2.0.0\n")
        git(seed, "add", "-A")
        git(seed, "-c", "user.name=Seed", "-c", "user.email=seed@example.com", "commit", "--quiet", "-m", "Bump")
        git(seed, "push", "--quiet", remote.path, "HEAD:refs/heads/$branch")
    }

    @Test
    fun `Clone leaves a work tree without any Git pointer`() {
        assertEquals(0, harness(KubernetesHarnessScripts.CLONE_SCRIPT).exitCode)
        assertEquals("version = 2.0.0\n", File(workTree, "version.properties").readText())
        assertFalse(File(workTree, ".git").exists(), "No .git in the work tree")
        assertTrue(File(gitDir, "HEAD").exists(), "Git directory apart")
    }

    @Test
    fun `No change is a success without a commit`() {
        harness(KubernetesHarnessScripts.CLONE_SCRIPT)
        val before = remoteHead()
        val push = harness(KubernetesHarnessScripts.PUSH_SCRIPT)
        assertEquals(0, push.exitCode, push.output)
        assertEquals(before, remoteHead())
    }

    @Test
    fun `Every change ends up in one commit of the harness on the upgrade branch`() {
        harness(KubernetesHarnessScripts.CLONE_SCRIPT)
        val before = remoteHead()
        // The user's step
        File(workTree, "version.properties").writeText("version = 2.0.0\nlocked = true\n")
        File(workTree, "locks").mkdirs()
        File(workTree, "locks/gradle.lockfile").writeText("lock\n")
        val message = "Locks for \$(touch ${dir.path}/pwned) `id`"
        val push = harness(KubernetesHarnessScripts.PUSH_SCRIPT, commitMessage = message)
        assertEquals(0, push.exitCode, push.output)
        // One commit on top of the bump
        assertEquals(before, remoteGit("rev-parse", "$branch~1"))
        assertEquals(message, remoteGit("log", "-1", "--format=%s", branch))
        assertEquals("Yontrack <yontrack@example.com>", remoteGit("log", "-1", "--format=%an <%ae>", branch))
        assertEquals("Yontrack <yontrack@example.com>", remoteGit("log", "-1", "--format=%cn <%ce>", branch))
        assertEquals(
            setOf("locks/gradle.lockfile", "version.properties"),
            remoteGit("diff", "--name-only", "$branch~1", branch).lines().toSet()
        )
        assertFalse(File(dir, "pwned").exists(), "The commit message is never evaluated")
    }

    @Test
    fun `What the user step plants in the work tree is not run by the push`() {
        harness(KubernetesHarnessScripts.CLONE_SCRIPT)
        // A hook and a configuration in a planted .git directory, and a filter driver
        File(workTree, ".git/hooks").mkdirs()
        File(workTree, ".git/hooks/pre-commit").apply {
            writeText("#!/bin/sh\ntouch ${dir.path}/hooked\n")
            setExecutable(true)
        }
        File(workTree, ".git/config").writeText("[filter \"x\"]\n\tclean = touch ${dir.path}/filtered\n")
        File(workTree, ".gitattributes").writeText("* filter=x\n")
        File(workTree, "change.txt").writeText("change\n")
        val push = harness(KubernetesHarnessScripts.PUSH_SCRIPT)
        assertEquals(0, push.exitCode, push.output)
        assertFalse(File(dir, "hooked").exists(), "Planted hook not run")
        assertFalse(File(dir, "filtered").exists(), "Planted filter driver not run")
        assertTrue("change.txt" in remoteGit("ls-tree", "-r", "--name-only", branch))
    }

    private class Result(val exitCode: Int, val output: String)

    private fun harness(script: String, commitMessage: String = "Post processing"): Result {
        val process = ProcessBuilder("sh", "-c", script)
            .directory(dir)
            .redirectErrorStream(true)
            .apply {
                environment().apply {
                    put("HOME", File(dir, "home").path)
                    put("GIT_TERMINAL_PROMPT", "0")
                    put("GIT_CONFIG_NOSYSTEM", "1")
                    put("GIT_CONFIG_GLOBAL", "/dev/null")
                    put(KubernetesHarnessScripts.ENV_CLONE_URL, remote.toURI().toString().replace("file:/", "file:///"))
                    put(KubernetesHarnessScripts.ENV_UPGRADE_BRANCH, branch)
                    put(KubernetesHarnessScripts.ENV_GIT_DIR, gitDir.path)
                    put(KubernetesHarnessScripts.ENV_WORK_TREE, workTree.path)
                    put(KubernetesHarnessScripts.ENV_CREDENTIALS_DIR, File(dir, "credentials").path)
                    put(KubernetesHarnessScripts.ENV_COMMIT_MESSAGE, commitMessage)
                    put("GIT_AUTHOR_NAME", "Yontrack")
                    put("GIT_AUTHOR_EMAIL", "yontrack@example.com")
                    put("GIT_COMMITTER_NAME", "Yontrack")
                    put("GIT_COMMITTER_EMAIL", "yontrack@example.com")
                }
            }
            .start()
        val output = process.inputStream.bufferedReader().readText()
        assertTrue(process.waitFor(60, TimeUnit.SECONDS), "Script timed out")
        return Result(process.exitValue(), output)
    }

    private fun remoteHead() = remoteGit("rev-parse", branch)

    private fun remoteGit(vararg args: String) = git(remote, *args).trim()

    private fun git(directory: File, vararg args: String): String {
        val process = ProcessBuilder("git", *args)
            .directory(directory)
            .redirectErrorStream(true)
            .apply {
                environment()["GIT_CONFIG_NOSYSTEM"] = "1"
                environment()["GIT_CONFIG_GLOBAL"] = "/dev/null"
            }
            .start()
        val output = process.inputStream.bufferedReader().readText()
        assertTrue(process.waitFor(60, TimeUnit.SECONDS), "git timed out")
        assertEquals(0, process.exitValue(), "git ${args.joinToString(" ")}: $output")
        return output
    }

    private fun available(vararg command: String): Boolean =
        try {
            ProcessBuilder(*command).redirectErrorStream(true).start().let {
                it.inputStream.readAllBytes()
                it.waitFor(30, TimeUnit.SECONDS) && it.exitValue() == 0
            }
        } catch (_: Exception) {
            false
        }
}
