package net.nemerosa.ontrack.extension.bitbucket.cloud.property

import net.nemerosa.ontrack.extension.bitbucket.cloud.client.BitbucketCloudClientFactory
import net.nemerosa.ontrack.extension.git.model.GitConfiguration
import net.nemerosa.ontrack.extension.git.model.GitConfigurator
import net.nemerosa.ontrack.extension.git.model.GitPullRequest
import net.nemerosa.ontrack.extension.issues.IssueServiceRegistry
import net.nemerosa.ontrack.extension.issues.model.ConfiguredIssueService
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.PropertyService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException

@Component
class BitbucketCloudConfigurator(
    private val propertyService: PropertyService,
    private val issueServiceRegistry: IssueServiceRegistry,
    private val clientFactory: BitbucketCloudClientFactory,
): GitConfigurator {

    private val logger: Logger = LoggerFactory.getLogger(BitbucketCloudConfigurator::class.java)

    override fun isProjectConfigured(project: Project): Boolean =
        propertyService.hasProperty(project, BitbucketCloudProjectConfigurationPropertyType::class.java)

    override fun getConfiguration(project: Project): GitConfiguration? =
        propertyService.getProperty(project, BitbucketCloudProjectConfigurationPropertyType::class.java)
            .value
            ?.run {
                BitbucketCloudGitConfiguration(
                    this,
                    getConfiguredIssueService(this)
                )
            }

    /**
     * Pull request from `GET /pullrequests/{id}`, `null` when not found or when Bitbucket Cloud cannot be reached.
     */
    override fun getPullRequest(configuration: GitConfiguration, id: Int): GitPullRequest? =
        if (configuration is BitbucketCloudGitConfiguration) {
            val property = configuration.property
            try {
                clientFactory.getBitbucketCloudClient(property.configuration)
                    .getPullRequest(property.workspace, property.repository, id)
                    ?.let { pr ->
                        GitPullRequest(
                            id = pr.id,
                            key = "#${pr.id}",
                            source = pr.source?.branch?.name ?: "",
                            target = pr.destination?.branch?.name ?: "",
                            title = pr.title ?: "",
                            status = pr.state ?: "",
                            url = pr.links?.html?.href?.takeIf { it.isNotBlank() }
                                ?: "${property.repositoryUrl}/pull-requests/${pr.id}",
                        )
                    }
            } catch (any: RestClientException) {
                logger.error("Cannot get pull request #$id from ${property.fullName}", any)
                null
            }
        } else {
            null
        }

    private fun getConfiguredIssueService(property: BitbucketCloudProjectConfigurationProperty): ConfiguredIssueService? =
        property.issueServiceConfigurationIdentifier
            ?.takeIf { it.isNotBlank() }
            ?.let { issueServiceRegistry.getConfiguredIssueService(it) }
}