package net.nemerosa.ontrack.extension.bitbucket.cloud.property

import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudAuthType
import net.nemerosa.ontrack.extension.git.model.GitConfiguration
import net.nemerosa.ontrack.extension.issues.model.ConfiguredIssueService
import net.nemerosa.ontrack.git.GitRepositoryAuthenticator
import net.nemerosa.ontrack.git.UsernamePasswordGitRepositoryAuthenticator

class BitbucketCloudGitConfiguration(
    val property: BitbucketCloudProjectConfigurationProperty,
    override val configuredIssueService: ConfiguredIssueService?
) : GitConfiguration {

    companion object {
        /**
         * Static user name documented by Atlassian for Git over HTTPS with an API token
         * (the Atlassian account email is refused).
         */
        const val API_TOKEN_GIT_USERNAME = "x-bitbucket-api-token-auth"

        /**
         * Static user name for Git over HTTPS with a workspace, project or repository access token.
         */
        const val ACCESS_TOKEN_GIT_USERNAME = "x-token-auth"
    }

    override val type: String = "bitbucket-cloud"

    override val name: String = property.configuration.name

    override val remote: String = "${property.repositoryUrl}.git"

    override val authenticator: GitRepositoryAuthenticator?
        get() = property.configuration.run {
            UsernamePasswordGitRepositoryAuthenticator(
                username = when (authType) {
                    BitbucketCloudAuthType.API_TOKEN -> API_TOKEN_GIT_USERNAME
                    BitbucketCloudAuthType.ACCESS_TOKEN -> ACCESS_TOKEN_GIT_USERNAME
                },
                password = token ?: "",
            )
        }

    override val commitLink: String = "${property.repositoryUrl}/commits/{commit}"

    override val fileAtCommitLink: String = "${property.repositoryUrl}/src/{commit}/{path}"

    override val indexationInterval: Int = property.indexationInterval

}
