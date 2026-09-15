package net.nemerosa.ontrack.extension.bitbucket.cloud.settings

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.model.annotations.APILabel

/**
 * General settings for Bitbucket Cloud.
 *
 * @property maxCommits Maximum number of commits to return for a change log
 */
data class BitbucketCloudSettings(
    @APILabel("Max commits")
    @APIDescription("Maximum number of commits to return for a change log. Bitbucket Cloud allows 1,000 API requests per hour per token and returns at most 100 commits per request.")
    val maxCommits: Int = DEFAULT_MAX_COMMITS,
) {
    companion object {
        /**
         * Aligned with Bitbucket Server.
         */
        const val DEFAULT_MAX_COMMITS: Int = 1000
    }
}
