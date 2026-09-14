package net.nemerosa.ontrack.extension.bitbucket.cloud

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Unique names for what the real tests create in the shared test workspace, so that parallel CI shards,
 * worktrees and developers never collide.
 *
 * A test branch is `yontrack-test-<UTC creation time>-<run id>-<random>-<name>`. The creation time is in the
 * name, not read from the API, because a branch created from `main` carries `main`'s commit date:
 * [BitbucketCloudTestCleanup] would otherwise delete another run's brand-new branch.
 *
 * @param runId Identifies the run, see [runId]
 * @param clock Current time
 */
class BitbucketCloudTestNames(
    private val runId: String = runId { System.getenv(it) },
    private val clock: () -> Instant = Instant::now,
) {

    fun branch(name: String): String {
        val timestamp = FORMAT.format(LocalDateTime.ofInstant(clock().truncatedTo(ChronoUnit.SECONDS), ZoneOffset.UTC))
        val random = (1..6).map { ALPHABET.random() }.joinToString("")
        return "$PREFIX$timestamp-$runId-$random-${sanitize(name)}"
    }

    companion object {
        const val PREFIX = "yontrack-test-"

        private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"
        private val FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
        private val PATTERN = Regex("^$PREFIX(\\d{8}T\\d{6}Z)-.+$")

        /**
         * Creation time of a test branch, or `null` when the branch was not named by [branch].
         */
        fun createdAt(branch: String): Instant? =
            PATTERN.matchEntire(branch)?.let { match ->
                runCatching {
                    LocalDateTime.parse(match.groupValues[1], FORMAT).toInstant(ZoneOffset.UTC)
                }.getOrNull()
            }

        /**
         * The GitHub Actions run and attempt, or `local` outside of CI.
         */
        fun runId(lookup: (String) -> String?): String {
            val id = lookup("GITHUB_RUN_ID")
            return if (id.isNullOrBlank()) {
                "local"
            } else {
                listOfNotNull(id, lookup("GITHUB_RUN_ATTEMPT")?.takeIf { it.isNotBlank() }).joinToString("-")
            }
        }

        private fun sanitize(name: String) =
            name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
    }
}
