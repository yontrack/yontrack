package net.nemerosa.ontrack.extension.git.mocking

import net.nemerosa.ontrack.extension.git.model.GitConfiguration
import net.nemerosa.ontrack.extension.issues.model.ConfiguredIssueService
import net.nemerosa.ontrack.git.GitRepositoryAuthenticator

/**
 * Git configuration of a project whose repository is a local one, for testing the Git machinery
 * which GitHub, GitLab and Bitbucket share, without any of them.
 */
class LocalGitConfiguration(
    override val name: String,
    override val remote: String,
    override val configuredIssueService: ConfiguredIssueService?,
) : GitConfiguration {

    override val type: String = TYPE

    override val authenticator: GitRepositoryAuthenticator? = null

    override val commitLink: String = ""

    override val fileAtCommitLink: String = ""

    override val indexationInterval: Int = 0

    companion object {
        const val TYPE = "local"
    }
}
