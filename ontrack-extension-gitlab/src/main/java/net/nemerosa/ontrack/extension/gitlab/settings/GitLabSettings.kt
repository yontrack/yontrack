package net.nemerosa.ontrack.extension.gitlab.settings

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.model.annotations.APILabel

/**
 * General settings for GitLab.
 *
 * @property maxCommits Maximum number of commits to return for a change log
 */
data class GitLabSettings(
    @APILabel("Max commits")
    @APIDescription("Maximum number of commits to return for a change log. GitLab's comparison endpoint returns the whole range in one answer, and gives up on its own past a few thousand commits.")
    val maxCommits: Int = DEFAULT_MAX_COMMITS,
) {
    companion object {
        /**
         * Aligned with GitHub, Bitbucket Server and Bitbucket Cloud.
         */
        const val DEFAULT_MAX_COMMITS: Int = 1000
    }
}
