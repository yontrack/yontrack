package net.nemerosa.ontrack.extension.stash.client

import net.nemerosa.ontrack.common.BaseException

/**
 * Bitbucket Server answered the merge of a PR with a `409 Conflict`.
 *
 * This happens when the PR cannot be merged at the time of the call, typically because the
 * target branch received new commits since the PR was found mergeable. The merge may succeed
 * once the PR has been checked again.
 *
 * @property prId ID of the PR
 */
class BitbucketServerPRMergeRejectedException(
    val prId: Int,
    reason: String,
) : BaseException("Bitbucket Server rejected the merge of PR $prId: $reason")
