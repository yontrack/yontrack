package net.nemerosa.ontrack.extension.git.mocking

import net.nemerosa.ontrack.extension.git.model.GitConfiguration
import net.nemerosa.ontrack.extension.git.model.GitConfigurator
import net.nemerosa.ontrack.extension.git.model.GitPullRequest
import net.nemerosa.ontrack.extension.issues.IssueServiceRegistry
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.PropertyService
import org.springframework.stereotype.Component

/**
 * [GitConfigurator] for the projects associated with a local Git repository, for testing.
 */
@Component
class LocalGitConfigurator(
    private val propertyService: PropertyService,
    private val issueServiceRegistry: IssueServiceRegistry,
) : GitConfigurator {

    override fun isProjectConfigured(project: Project): Boolean =
        propertyService.hasProperty(project, LocalGitProjectConfigurationPropertyType::class.java)

    override fun getConfiguration(project: Project): GitConfiguration? =
        propertyService.getPropertyValue(project, LocalGitProjectConfigurationPropertyType::class.java)
            ?.let { property ->
                LocalGitConfiguration(
                    name = property.name,
                    remote = property.remote,
                    configuredIssueService = property.issueServiceConfigurationIdentifier
                        ?.takeIf { it.isNotBlank() }
                        ?.let { issueServiceRegistry.getConfiguredIssueService(it) },
                )
            }

    /**
     * No pull request in a local repository.
     */
    override fun toPullRequestID(key: String): Int? = null

    /**
     * No pull request in a local repository.
     */
    override fun getPullRequest(configuration: GitConfiguration, id: Int): GitPullRequest? = null
}
