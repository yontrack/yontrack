package net.nemerosa.ontrack.extension.bitbucket.cloud.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import net.nemerosa.ontrack.common.BaseException

/**
 * Outcome of an attempt to merge a pull request.
 */
enum class BitbucketCloudMergeOutcome {
    /**
     * The pull request is merged.
     */
    MERGED,

    /**
     * Bitbucket Cloud accepted the merge but runs it asynchronously: the state of the pull request tells when it
     * is done.
     */
    PENDING,

    /**
     * The pull request cannot be merged yet: merge checks not passing, conflicting refs, or a merge which timed
     * out on the Bitbucket side. Worth retrying later.
     */
    NOT_MERGEABLE,
}

/**
 * Member of a workspace, used to resolve reviewers.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class BitbucketCloudWorkspaceMember(
    val user: BitbucketCloudAccount?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BitbucketCloudAccount(
    val uuid: String?,
    val account_id: String?,
    val nickname: String?,
    val display_name: String?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BitbucketCloudWorkspaceMemberList(
    override val values: List<BitbucketCloudWorkspaceMember>,
    override val next: String?,
    override val page: Int = 0,
) : BitbucketCloudPaginatedList<BitbucketCloudWorkspaceMember>

@JsonIgnoreProperties(ignoreUnknown = true)
data class BitbucketCloudCommitStatus(
    val state: String?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BitbucketCloudCommitStatusList(
    override val values: List<BitbucketCloudCommitStatus>,
    override val next: String?,
    override val page: Int = 0,
) : BitbucketCloudPaginatedList<BitbucketCloudCommitStatus>

class BitbucketCloudReviewerNotFoundException(workspace: String, reviewer: String) : BaseException(
    """Reviewer $reviewer cannot be found among the members of the $workspace workspace. Use their UUID, account ID, nickname or display name."""
)
