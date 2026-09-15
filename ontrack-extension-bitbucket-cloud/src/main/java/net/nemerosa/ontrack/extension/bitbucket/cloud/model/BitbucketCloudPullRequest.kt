package net.nemerosa.ontrack.extension.bitbucket.cloud.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Pull request in Bitbucket Cloud.
 *
 * @property id Number of the pull request in its repository
 * @property title Title
 * @property state `OPEN`, `MERGED`, `DECLINED` or `SUPERSEDED`
 * @property source Source branch
 * @property destination Target branch
 * @property links Links, including the HTML page of the pull request
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class BitbucketCloudPullRequest(
    val id: Int,
    val title: String?,
    val state: String?,
    val source: BitbucketCloudPullRequestEndpoint?,
    val destination: BitbucketCloudPullRequestEndpoint?,
    val links: BitbucketCloudLinks?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BitbucketCloudPullRequestEndpoint(
    val branch: BitbucketCloudBranchName?,
)

/**
 * Branch reference: a name, and for a branch of `refs/branches`, its last commit.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class BitbucketCloudBranchName(
    val name: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BitbucketCloudBranch(
    val name: String,
    val target: BitbucketCloudCommit?,
)
