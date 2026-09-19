package net.nemerosa.ontrack.extension.gitlab.settings

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.model.annotations.APILabel
import java.time.Duration

/**
 * General settings for GitLab.
 *
 * There is deliberately **no merge strategy** here, as there is on Bitbucket Cloud: GitLab's merge API
 * offers `squash` and `should_remove_source_branch` and nothing equivalent to a three-way choice.
 *
 * @property maxCommits Maximum number of commits to return for a change log
 * @property squash Squash the commits of an auto-versioning merge request when it is merged
 * @property removeSourceBranch Delete the source branch when an auto-versioning merge request is merged
 * @property autoMergeTimeout Number of milliseconds to wait for an auto-versioning merge request to become
 * mergeable
 * @property autoMergeInterval Number of milliseconds to wait between two checks
 */
data class GitLabSettings(
    @APILabel("Max commits")
    @APIDescription("Maximum number of commits to return for a change log. GitLab's comparison endpoint returns the whole range in one answer, and gives up on its own past a few thousand commits.")
    val maxCommits: Int = DEFAULT_MAX_COMMITS,
    @APILabel("Squash")
    @APIDescription("Squash the commits of an auto-versioning merge request when it is merged. GitLab's project settings can force this either way, whatever is asked for here.")
    val squash: Boolean = DEFAULT_SQUASH,
    @APILabel("Remove source branch")
    @APIDescription("Delete the source branch when an auto-versioning merge request is merged")
    val removeSourceBranch: Boolean = DEFAULT_REMOVE_SOURCE_BRANCH,
    @APILabel("Auto merge timeout")
    @APIDescription("Number of milliseconds to wait for an auto-versioning merge request to become mergeable")
    val autoMergeTimeout: Long = DEFAULT_AUTO_MERGE_TIMEOUT,
    @APILabel("Auto merge interval")
    @APIDescription("Number of milliseconds to wait between two checks of the detailed merge status of an auto-versioning merge request. Each check costs one API request.")
    val autoMergeInterval: Long = DEFAULT_AUTO_MERGE_INTERVAL,
) {
    companion object {
        /**
         * Aligned with GitHub, Bitbucket Server and Bitbucket Cloud.
         */
        const val DEFAULT_MAX_COMMITS: Int = 1000

        /**
         * Aligned with Bitbucket Cloud, whose default merge strategy is `squash`: an auto-versioning branch
         * carries one logical change however many commits a post-processing left on it.
         */
        const val DEFAULT_SQUASH: Boolean = true

        /**
         * Aligned with Bitbucket Cloud's `autoDeleteBranch`.
         */
        const val DEFAULT_REMOVE_SOURCE_BRANCH: Boolean = true

        /**
         * Aligned with GitHub, Bitbucket Server and Bitbucket Cloud.
         */
        val DEFAULT_AUTO_MERGE_TIMEOUT: Long = Duration.ofMinutes(10).toMillis()

        /**
         * Aligned with GitHub, Bitbucket Server and Bitbucket Cloud.
         */
        val DEFAULT_AUTO_MERGE_INTERVAL: Long = Duration.ofSeconds(30).toMillis()
    }
}
