package net.nemerosa.ontrack.extension.bitbucket.cloud.settings

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.model.annotations.APILabel
import java.time.Duration

/**
 * General settings for Bitbucket Cloud.
 *
 * @property maxCommits Maximum number of commits to return for a change log
 * @property mergeStrategy Strategy used to merge the auto-versioning pull requests
 * @property autoMergeTimeout Number of milliseconds to wait for an auto-versioning pull request to be mergeable
 * @property autoMergeInterval Number of milliseconds to wait between two attempts to merge
 * @property autoDeleteBranch Deleting the source branch when an auto-versioning pull request is merged
 */
data class BitbucketCloudSettings(
    @APILabel("Max commits")
    @APIDescription("Maximum number of commits to return for a change log. Bitbucket Cloud allows 1,000 API requests per hour per token and returns at most 100 commits per request.")
    val maxCommits: Int = DEFAULT_MAX_COMMITS,
    @APILabel("Merge strategy")
    @APIDescription("Strategy used to merge the auto-versioning pull requests: merge_commit, squash or fast_forward")
    val mergeStrategy: BitbucketCloudMergeStrategy = DEFAULT_MERGE_STRATEGY,
    @APILabel("Auto merge timeout")
    @APIDescription("Number of milliseconds to wait for an auto-versioning pull request to be mergeable")
    val autoMergeTimeout: Long = DEFAULT_AUTO_MERGE_TIMEOUT,
    @APILabel("Auto merge interval")
    @APIDescription("Number of milliseconds to wait between two attempts to merge an auto-versioning pull request. Each attempt costs up to three API requests.")
    val autoMergeInterval: Long = DEFAULT_AUTO_MERGE_INTERVAL,
    @APILabel("Auto delete branch")
    @APIDescription("Deleting the source branch when an auto-versioning pull request is merged")
    val autoDeleteBranch: Boolean = DEFAULT_AUTO_DELETE_BRANCH,
) {
    companion object {
        /**
         * Aligned with Bitbucket Server.
         */
        const val DEFAULT_MAX_COMMITS: Int = 1000

        val DEFAULT_MERGE_STRATEGY = BitbucketCloudMergeStrategy.squash

        /**
         * Aligned with Bitbucket Server.
         */
        val DEFAULT_AUTO_MERGE_TIMEOUT: Long = Duration.ofMinutes(10).toMillis()

        /**
         * Aligned with Bitbucket Server.
         */
        val DEFAULT_AUTO_MERGE_INTERVAL: Long = Duration.ofSeconds(30).toMillis()

        const val DEFAULT_AUTO_DELETE_BRANCH: Boolean = true
    }
}

/**
 * Merge strategies of Bitbucket Cloud offered for the auto-versioning pull requests. The names are the values
 * of the Bitbucket Cloud API.
 */
@Suppress("EnumEntryName")
enum class BitbucketCloudMergeStrategy {
    merge_commit,
    squash,
    fast_forward,
}
