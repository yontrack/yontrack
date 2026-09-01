package net.nemerosa.ontrack.demo.seed

import java.io.File
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

/**
 * One commit since the last release, which the seed turns into one build.
 *
 * @property id Short commit hash, used as the build name — deterministic, unlike a counter
 * that would shift as soon as one commit lands.
 * @property message Commit subject, shown as the build description.
 * @property time Author time, used as the build creation time so the branch reads in the
 * order the work actually happened.
 */
data class ChangelogEntry(
    val id: String,
    val message: String,
    val time: LocalDateTime,
)

/**
 * Where the changelog project's content comes from.
 *
 * The demo carries one project seeded from the real changelog since the last release, so
 * that it keeps showing current work between the times someone remembers to extend the
 * curated dataset.
 */
fun interface ChangelogSource {
    fun entries(): List<ChangelogEntry>
}

/**
 * Reads the changelog out of a git checkout: every commit between the last release tag and
 * `HEAD`.
 *
 * Yields nothing rather than failing when git cannot answer — a checkout with no tags (the
 * default shallow `actions/checkout`) or no git at all. The demo is then one project poorer,
 * which is worth less than a reset that refuses to run.
 */
class GitChangelogSource(
    private val directory: File,
    private val max: Int = DEFAULT_MAX,
    private val warn: (String) -> Unit = {},
    private val git: (List<String>) -> String? = { command -> runGit(directory, command, warn) },
) : ChangelogSource {

    override fun entries(): List<ChangelogEntry> {
        val tag = git(listOf("describe", "--tags", "--abbrev=0", "--match", TAG_PATTERN))?.trim()
        if (tag.isNullOrBlank()) {
            warn("No release tag found in $directory - the changelog project will have no build.")
            return emptyList()
        }
        val log = git(listOf("log", "$tag..HEAD", "--no-merges", "--pretty=format:$FORMAT"))
        if (log == null) {
            warn("Could not read the git log since $tag - the changelog project will have no build.")
            return emptyList()
        }
        return parseLog(log).take(max)
    }

    companion object {

        /**
         * How many commits the changelog project shows. Enough to fill a branch view;
         * not so many that a long release turns the demo into a wall of builds.
         */
        const val DEFAULT_MAX = 25

        /**
         * Release tags are bare versions (`5.2.2`). Anything else — the experimental and
         * feature tags this repository also carries — is not a release.
         */
        const val TAG_PATTERN = "[0-9]*.[0-9]*.[0-9]*"

        /**
         * Unit separators rather than a printable character: a commit subject can contain
         * anything, including whatever separator looked safe.
         */
        private const val FORMAT = "%h%x1f%s%x1f%aI"

        /** The `%x1f` git emits, on this side. */
        private const val SEPARATOR = '\u001F'

        /**
         * Parses the output of the `log` command above. Malformed lines are skipped: a
         * demo missing one build beats a reset that dies on an odd commit.
         */
        fun parseLog(log: String): List<ChangelogEntry> =
            log.lineSequence()
                .mapNotNull { line ->
                    val parts = line.split(SEPARATOR)
                    if (parts.size != 3) return@mapNotNull null
                    val (id, message, time) = parts
                    if (id.isBlank() || time.isBlank()) return@mapNotNull null
                    val parsed = runCatching { OffsetDateTime.parse(time) }.getOrNull()
                        ?: return@mapNotNull null
                    ChangelogEntry(
                        id = id.trim(),
                        message = message.trim(),
                        time = parsed.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime(),
                    )
                }
                .toList()

        /**
         * Runs one git command, returning null on anything that is not a clean success.
         */
        private fun runGit(directory: File, command: List<String>, warn: (String) -> Unit): String? =
            try {
                val process = ProcessBuilder(listOf("git") + command)
                    .directory(directory)
                    .redirectErrorStream(false)
                    .start()
                val output = process.inputStream.bufferedReader().readText()
                if (!process.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    process.destroy()
                    warn("git ${command.joinToString(" ")} timed out.")
                    null
                } else if (process.exitValue() != 0) {
                    null
                } else {
                    output
                }
            } catch (ex: Exception) {
                warn("Could not run git ${command.joinToString(" ")}: ${ex.message}")
                null
            }

        private const val GIT_TIMEOUT_SECONDS = 30L
    }
}
