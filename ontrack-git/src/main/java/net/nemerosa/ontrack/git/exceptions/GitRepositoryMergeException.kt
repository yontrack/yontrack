package net.nemerosa.ontrack.git.exceptions

/**
 * Thrown when a merge operation fails because of a conflict or other merge failure.
 */
class GitRepositoryMergeException(
    remote: String,
    head: String,
    base: String,
    status: String,
) : GitRepositoryException(
    "Cannot merge branch $head into $base in $remote: $status"
)
