package net.nemerosa.ontrack.extension.git.model

import net.nemerosa.ontrack.git.GitRepository

/**
 * Gets the Git repository for this configuration
 */
val GitConfiguration.gitRepository: GitRepository
    get() = GitRepository(
        type = type,
        name = name,
        remote = remote,
        authenticator = authenticator,
    )

/**
 * Gets the link to a given commit.
 */
fun GitConfiguration.getCommitLink(commit: String): String = commitLink.replace("{commit}", commit)
