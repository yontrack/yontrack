package net.nemerosa.ontrack.extension.gitlab.property

import net.nemerosa.ontrack.extension.git.model.GitConfiguration
import net.nemerosa.ontrack.extension.git.model.GitConfigurator
import net.nemerosa.ontrack.extension.git.model.GitPullRequest
import net.nemerosa.ontrack.extension.gitlab.GitLabIssueServiceExtension
import net.nemerosa.ontrack.extension.gitlab.client.GitLabClientFactory
import net.nemerosa.ontrack.extension.gitlab.model.GitLabIssueServiceConfiguration
import net.nemerosa.ontrack.extension.issues.IssueServiceRegistry
import net.nemerosa.ontrack.extension.issues.model.ConfiguredIssueService
import net.nemerosa.ontrack.extension.issues.model.IssueServiceConfigurationRepresentation.Companion.isSelf
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.PropertyService
import org.springframework.stereotype.Component

@Component
class GitLabConfigurator(
        private val propertyService: PropertyService,
        private val issueServiceRegistry: IssueServiceRegistry,
        private val issueServiceExtension: GitLabIssueServiceExtension,
        private val gitLabClientFactory: GitLabClientFactory
) : GitConfigurator {

    override fun isProjectConfigured(project: Project): Boolean {
        return propertyService.hasProperty(project, GitLabProjectConfigurationPropertyType::class.java)
    }

    override fun getConfiguration(project: Project): GitConfiguration? {
        return propertyService.getProperty(project, GitLabProjectConfigurationPropertyType::class.java)
                .value
                ?.run { getGitConfiguration(this) }
    }

    /**
     * A GitLab merge request is Yontrack's pull request: the mapping is done here rather than in the client,
     * which stays on GitLab's own model.
     */
    override fun getPullRequest(configuration: GitConfiguration, id: Int): GitPullRequest? =
            if (configuration is GitLabGitConfiguration) {
                val client = gitLabClientFactory.create(configuration.property.configuration)
                client.getMergeRequest(
                        configuration.property.repository,
                        id
                )?.let { mergeRequest ->
                    GitPullRequest(
                            id,
                            "#$id",
                            mergeRequest.source_branch,
                            mergeRequest.target_branch,
                            mergeRequest.title,
                            mergeRequest.state,
                            mergeRequest.web_url
                    )
                }
            } else {
                null
            }

    private fun getGitConfiguration(property: GitLabProjectConfigurationProperty): GitConfiguration {
        return GitLabGitConfiguration(
                property,
                getConfiguredIssueService(property)
        )
    }

    private fun getConfiguredIssueService(property: GitLabProjectConfigurationProperty): ConfiguredIssueService? {
        val identifier = property.issueServiceConfigurationIdentifier
        return if (identifier.isNullOrBlank() || isSelf(identifier)) {
            ConfiguredIssueService(
                    issueServiceExtension,
                    GitLabIssueServiceConfiguration(
                            property.configuration,
                            property.repository
                    )
            )
        } else {
            issueServiceRegistry.getConfiguredIssueService(identifier)
        }
    }

}