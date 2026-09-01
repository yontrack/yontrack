package net.nemerosa.ontrack.demo.seed

import java.io.File

/**
 * What the seed program needs to know, read from the environment.
 *
 * @property url Yontrack instance to reset.
 * @property token API token for that instance.
 * @property repository Checkout the changelog project is read from.
 * @property changelogMax How many commits the changelog project shows.
 */
data class DemoSeedConfig(
    val url: String,
    val token: String,
    val repository: File,
    val changelogMax: Int,
) {

    companion object {

        const val URL = "YONTRACK_URL"
        const val TOKEN = "YONTRACK_TOKEN"
        const val REPOSITORY = "DEMO_SEED_REPOSITORY"
        const val CHANGELOG_MAX = "DEMO_SEED_CHANGELOG_MAX"
        const val URL_PATTERN = "DEMO_SEED_URL_PATTERN"

        /**
         * The instances this program is allowed to wipe: a demo, or a local instance.
         *
         * This program deletes every project on whatever it is pointed at, and the URL of
         * a production instance differs from the demo's by a few characters. The guard is
         * overridable through [URL_PATTERN] — someone naming another instance on purpose
         * is a decision, pasting the wrong URL is an accident.
         */
        const val DEFAULT_URL_PATTERN = "^https?://(demo\\.|localhost(?=[:/]|$)|127\\.0\\.0\\.1(?=[:/]|$))"

        /**
         * Reads the configuration, or fails with what is missing.
         */
        fun from(env: Map<String, String>): DemoSeedConfig {
            val url = env[URL]?.trim()?.trimEnd('/')
            require(!url.isNullOrBlank()) { "$URL is not set: nothing to seed." }
            val token = env[TOKEN]?.trim()
            require(!token.isNullOrBlank()) { "$TOKEN is not set: the seed cannot authenticate." }

            val pattern = env[URL_PATTERN]?.trim()?.takeIf { it.isNotBlank() } ?: DEFAULT_URL_PATTERN
            require(Regex(pattern).containsMatchIn(url)) {
                "$url does not look like a demo instance (it does not match $pattern). " +
                        "This program deletes every project on the instance it is pointed at. " +
                        "Set $URL_PATTERN if you really mean to reset it."
            }

            val max = env[CHANGELOG_MAX]?.trim()?.takeIf { it.isNotBlank() }?.let {
                it.toIntOrNull() ?: throw IllegalArgumentException("$CHANGELOG_MAX is not a number: $it")
            } ?: GitChangelogSource.DEFAULT_MAX

            return DemoSeedConfig(
                url = url,
                token = token,
                repository = File(env[REPOSITORY]?.trim()?.takeIf { it.isNotBlank() } ?: "."),
                changelogMax = max,
            )
        }
    }
}
