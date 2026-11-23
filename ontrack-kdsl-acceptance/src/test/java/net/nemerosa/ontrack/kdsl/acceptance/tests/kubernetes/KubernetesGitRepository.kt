package net.nemerosa.ontrack.kdsl.acceptance.tests.kubernetes

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.TimeUnit

/**
 * A repository of the Git server of the Kubernetes acceptance stack, read from the host.
 *
 * It uses the `git` binary, as the HA tests do. The credentials are given to it through
 * `GIT_ASKPASS` and the environment of the process, so that they appear neither on a command line
 * nor in a log, and nothing is ever asked interactively.
 *
 * @property baseUrl Base URL of the Git server, without the organisation
 * @property organisation Organisation (user) owning the repositories on the server
 * @property repository Name of the repository, as the mock SCM names it (without `.git`)
 */
class KubernetesGitRepository(
    private val baseUrl: String,
    private val organisation: String,
    private val repository: String,
    private val username: String,
    private val password: String,
) {

    /** URL of the repository on the Git server, without any credentials */
    private val url: String = "${baseUrl.trimEnd('/')}/$organisation/$repository.git"

    /**
     * Name of the only branch starting with [prefix]: the upgrade branch the auto-versioning
     * derives from its pattern gets a suffix, which the test does not know.
     */
    fun branchStartingWith(prefix: String): String {
        val dir = Files.createTempDirectory("acc-kubernetes-git").toFile()
        try {
            val branches = git(dir, writeAskPass(dir), "ls-remote", "--heads", url)
                .lines().filter { it.isNotBlank() }
                .map { it.substringAfter("refs/heads/") }
            return branches.singleOrNull { it.startsWith(prefix) }
                ?: error("Expected exactly one branch starting with $prefix in $repository, found $branches")
        } finally {
            dir.deleteRecursively()
        }
    }

    /**
     * Clones one branch of the repository in a temporary directory, runs [code] on it, and deletes
     * the directory.
     */
    fun <T> withBranch(branch: String, code: (Clone) -> T): T {
        val dir = Files.createTempDirectory("acc-kubernetes-git").toFile()
        try {
            val clone = File(dir, "clone")
            val askpass = writeAskPass(dir)
            try {
                git(
                    dir, askpass,
                    "clone", "--quiet", "--single-branch", "--branch", branch, url, clone.absolutePath
                )
            } catch (e: IllegalStateException) {
                // What the server has, to understand a missing branch
                val branches = runCatching { git(dir, askpass, "ls-remote", "--heads", url) }.getOrElse { it.message }
                throw IllegalStateException("${e.message}\nBranches of $repository on the Git server:\n$branches", e)
            }
            return code(Clone(clone, askpass))
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun writeAskPass(dir: File): File {
        val script = File(dir, "askpass.sh")
        script.writeText(
            """
            #!/bin/sh
            case "${'$'}1" in
              Username*) printf '%s' "${'$'}ACC_GIT_USERNAME" ;;
              *) printf '%s' "${'$'}ACC_GIT_PASSWORD" ;;
            esac
            """.trimIndent() + "\n"
        )
        Files.setPosixFilePermissions(script.toPath(), PosixFilePermissions.fromString("rwx------"))
        return script
    }

    private fun git(dir: File, askpass: File, vararg args: String): String {
        val output = Files.createTempFile("acc-kubernetes-git", ".log").toFile()
        val builder = ProcessBuilder(
            listOf("git", "-c", "credential.helper=", "-c", "core.askPass=${askpass.absolutePath}") + args.toList()
        )
            .directory(dir)
            .redirectErrorStream(true)
            // To a file rather than a pipe: reading a pipe to its end would wait for the process,
            // however long it hangs
            .redirectOutput(output)
        builder.environment().apply {
            put("GIT_TERMINAL_PROMPT", "0")
            put("GIT_ASKPASS", askpass.absolutePath)
            put("GIT_CONFIG_GLOBAL", "/dev/null")
            put("GIT_CONFIG_NOSYSTEM", "1")
            put("ACC_GIT_USERNAME", username)
            put("ACC_GIT_PASSWORD", password)
        }
        try {
            val process = builder.start()
            if (!process.waitFor(2, TimeUnit.MINUTES)) {
                process.destroyForcibly()
                error("git ${args.first()} timed out: ${output.readText()}")
            }
            val text = output.readText()
            check(process.exitValue() == 0) { "git ${args.first()} failed: $text" }
            return text
        } finally {
            output.delete()
        }
    }

    /**
     * A clone of one branch.
     */
    inner class Clone(private val dir: File, private val askpass: File) {

        /** Subjects of the commits of the branch, the most recent first */
        val commits: List<String>
            get() = git(dir, askpass, "log", "--format=%s").lines().filter { it.isNotBlank() }

        /** Content of a file at the tip of the branch, `null` if there is none */
        fun file(path: String): String? =
            File(dir, path).takeIf { it.isFile }?.readText()

        /** Paths changed by the commit at the tip of the branch */
        val lastCommitFiles: List<String>
            get() = git(dir, askpass, "show", "--name-only", "--format=", "HEAD").lines().filter { it.isNotBlank() }
    }
}
