package net.nemerosa.ontrack.extension.git.model

import net.nemerosa.ontrack.extension.scm.service.SCMCloneAccess
import net.nemerosa.ontrack.extension.scm.service.SCMCloneCredentials
import net.nemerosa.ontrack.git.AnonymousGitRepositoryAuthenticator
import net.nemerosa.ontrack.git.AppTokenGitRepositoryAuthenticator
import net.nemerosa.ontrack.git.GitRepositoryAuthenticator
import net.nemerosa.ontrack.git.TokenGitRepositoryAuthenticator
import net.nemerosa.ontrack.git.UsernamePasswordGitRepositoryAuthenticator

/**
 * Username under which a token is presented to Git over HTTP(S). Same value as the JGit
 * credentials provider of the local clones (`JGitRepositoryAuthenticator`), which GitHub, GitLab
 * and Bitbucket all accept with a token as the password.
 */
const val GIT_TOKEN_USERNAME = "x-access-token"

/**
 * The [SCMCloneAccess] of a Git configuration: its remote, and the credentials of its
 * authenticator. An application token is fetched now.
 */
fun GitConfiguration.toSCMCloneAccess(): SCMCloneAccess =
    SCMCloneAccess(
        url = remote,
        credentials = authenticator?.toSCMCloneCredentials(),
    )

/**
 * Credentials of an authenticator, `null` for an anonymous one.
 */
fun GitRepositoryAuthenticator.toSCMCloneCredentials(): SCMCloneCredentials? = when (this) {
    is AnonymousGitRepositoryAuthenticator -> null
    is UsernamePasswordGitRepositoryAuthenticator -> SCMCloneCredentials(username, password)
    is TokenGitRepositoryAuthenticator -> SCMCloneCredentials(GIT_TOKEN_USERNAME, token)
    is AppTokenGitRepositoryAuthenticator -> SCMCloneCredentials(GIT_TOKEN_USERNAME, tokenAccess())
}
