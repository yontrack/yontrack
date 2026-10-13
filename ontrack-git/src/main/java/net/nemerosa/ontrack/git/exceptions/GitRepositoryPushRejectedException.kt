package net.nemerosa.ontrack.git.exceptions

/**
 * Thrown when the push of a merged branch is still rejected by the remote after the merge was
 * done again on its latest state.
 */
class GitRepositoryPushRejectedException(
    remote: String,
    head: String,
    base: String,
    attempts: Int,
    reason: String,
) : GitRepositoryException(
    "Cannot push the merge of $head into $base to $remote: rejected after $attempts attempts ($reason)"
)
