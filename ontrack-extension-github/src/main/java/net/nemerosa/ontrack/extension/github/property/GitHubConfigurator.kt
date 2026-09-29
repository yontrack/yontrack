package net.nemerosa.ontrack.extension.github.property

import net.nemerosa.ontrack.extension.git.model.GitConfiguration
import net.nemerosa.ontrack.extension.git.model.GitConfigurator
import net.nemerosa.ontrack.extension.git.model.GitPullRequest
import net.nemerosa.ontrack.extension.github.GitHubIssueServiceExtension
import net.nemerosa.ontrack.extension.github.app.GitHubAppNoTokenException
import net.nemerosa.ontrack.extension.github.app.GitHubAppTokenService
import net.nemerosa.ontrack.extension.github.client.OntrackGitHubClientFactory
import net.nemerosa.ontrack.extension.github.model.GitHubAuthenticationType
import net.nemerosa.ontrack.extension.github.model.GitHubEngineConfiguration
import net.nemerosa.ontrack.extension.github.model.getAppInstallationToken
import net.nemerosa.ontrack.extension.github.service.GitHubIssueServiceConfiguration
import net.nemerosa.ontrack.extension.issues.IssueServiceRegistry
import net.nemerosa.ontrack.extension.issues.model.ConfiguredIssueService
import net.nemerosa.ontrack.extension.issues.model.IssueServiceConfigurationRepresentation.Companion.isSelf
import net.nemerosa.ontrack.git.*
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.PropertyService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class GitHubConfigurator(
    private val propertyService: PropertyService,
    private val issueServiceRegistry: IssueServiceRegistry,
    private val issueServiceExtension: GitHubIssueServiceExtension,
    private val ontrackGitHubClientFactory: OntrackGitHubClientFactory,
    private val gitHubAppTokenService: GitHubAppTokenService,
) : GitConfigurator {

    private val logger: Logger = LoggerFactory.getLogger(GitHubConfigurator::class.java)

    override fun isProjectConfigured(project: Project): Boolean {
        return propertyService.hasProperty(project, GitHubProjectConfigurationPropertyType::class.java)
    }

    override fun getConfiguration(project: Project): GitConfiguration? {
        return propertyService.getProperty(project, GitHubProjectConfigurationPropertyType::class.java)
            .value
            ?.run { getGitConfiguration(this) }
    }

    override fun getPullRequest(configuration: GitConfiguration, id: Int): GitPullRequest? =
        if (configuration is GitHubGitConfiguration) {
            val client = ontrackGitHubClientFactory.create(configuration.property.configuration)
            try {
                client.getPR(
                    repository = configuration.property.repository,
                    pr = id,
                )?.let { pr ->
                    GitPullRequest(
                        id = id,
                        key = "#$id",
                        source = pr.head?.ref ?: "",
                        target = pr.base?.ref ?: "",
                        title = pr.title ?: "",
                        status = pr.state,
                        url = pr.html_url ?: "",
                    )
                }
            } catch (any: Exception) {
                logger.error("Cannot check PR for a branch", any)
                null
            }
        } else {
            null
        }

    private fun getGitConfiguration(property: GitHubProjectConfigurationProperty): GitConfiguration {
        return GitHubGitConfiguration(
            property,
            getConfiguredIssueService(property),
            getAuthenticator(property.configuration),
        )
    }

    private fun getAuthenticator(configuration: GitHubEngineConfiguration): GitRepositoryAuthenticator =
        when (configuration.authenticationType) {
            GitHubAuthenticationType.ANONYMOUS -> AnonymousGitRepositoryAuthenticator.INSTANCE
            GitHubAuthenticationType.PASSWORD -> UsernamePasswordGitRepositoryAuthenticator(
                configuration.user!!,
                configuration.password!!
            )
            GitHubAuthenticationType.USER_TOKEN -> UsernamePasswordGitRepositoryAuthenticator(
                configuration.user!!,
                configuration.oauth2Token!!
            )
            GitHubAuthenticationType.TOKEN -> TokenGitRepositoryAuthenticator(configuration.oauth2Token!!)
            GitHubAuthenticationType.APP -> AppTokenGitRepositoryAuthenticator(
                tokenAccess = {
                    gitHubAppTokenService.getAppInstallationToken(
                        configuration
                    ) ?: throw GitHubAppNoTokenException(configuration.appId!!)
                }
            )
        }

    private fun getConfiguredIssueService(property: GitHubProjectConfigurationProperty): ConfiguredIssueService? {
        val identifier = property.issueServiceConfigurationIdentifier
        return if (identifier.isNullOrBlank() || isSelf(identifier)) {
            ConfiguredIssueService(
                issueServiceExtension,
                GitHubIssueServiceConfiguration(
                    property.configuration,
                    property.repository
                )
            )
        } else {
            issueServiceRegistry.getConfiguredIssueService(identifier)
        }
    }

}