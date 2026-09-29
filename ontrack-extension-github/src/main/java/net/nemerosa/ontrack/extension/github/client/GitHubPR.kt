package net.nemerosa.ontrack.extension.github.client

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import net.nemerosa.ontrack.extension.scm.service.SCMPullRequestStatus

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubPR(
    /**
     * Local ID of the PR
     */
    val number: Int,
    /**
     * Merged status
     */
    val merged: Boolean,
    /**
     * State
     */
    val state: String,
    /**
     * Is the PR mergeable?
     */
    val mergeable: Boolean?,
    /**
     * Mergeable status
     */
    val mergeable_state: String?,
    /**
     * Link to the PR
     */
    val html_url: String?,
    /**
     * Title of the PR
     */
    val title: String? = null,
    /**
     * Source branch of the PR
     */
    val head: GitHubPRRef? = null,
    /**
     * Target branch of the PR
     */
    val base: GitHubPRRef? = null,
) {
    @JsonIgnore
    val status: SCMPullRequestStatus = when (state) {
        "open" -> SCMPullRequestStatus.OPEN
        "closed" -> when (merged) {
            true -> SCMPullRequestStatus.MERGED
            false -> SCMPullRequestStatus.DECLINED
        }

        else -> SCMPullRequestStatus.UNKNOWN
    }
}

/**
 * Branch end of a PR.
 *
 * @property ref Name of the branch
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubPRRef(
    val ref: String,
)
