package net.nemerosa.ontrack.extension.bitbucket.cloud.catalog

import net.nemerosa.ontrack.extension.bitbucket.cloud.client.BitbucketCloudClientFactory
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudConfiguration
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudConfigurationService
import net.nemerosa.ontrack.extension.bitbucket.cloud.property.BitbucketCloudProjectConfigurationProperty
import net.nemerosa.ontrack.extension.bitbucket.cloud.property.BitbucketCloudProjectConfigurationPropertyType
import net.nemerosa.ontrack.extension.scm.catalog.SCMCatalogEntry
import net.nemerosa.ontrack.extension.scm.catalog.SCMCatalogProvider
import net.nemerosa.ontrack.extension.scm.catalog.SCMCatalogSource
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.PropertyService
import net.nemerosa.ontrack.model.structure.StructureService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * SCM catalog for Bitbucket Cloud.
 *
 * A configuration no longer carries a workspace, and Bitbucket Cloud has no endpoint listing the workspaces
 * an API token or an access token can read (`/2.0/workspaces` is gone, `/2.0/user/workspaces` is refused to
 * access tokens). The workspaces to list are therefore those used by the Bitbucket Cloud properties of the
 * existing projects, per configuration: the catalog shows every repository of the workspaces Yontrack
 * already knows about.
 */
@Component
class BitbucketCloudSCMCatalogProvider(
    private val bitbucketCloudConfigurationService: BitbucketCloudConfigurationService,
    private val bitbucketCloudClientFactory: BitbucketCloudClientFactory,
    private val propertyService: PropertyService,
    private val structureService: StructureService,
    private val securityService: SecurityService,
) : SCMCatalogProvider {

    private val logger: Logger = LoggerFactory.getLogger(BitbucketCloudSCMCatalogProvider::class.java)

    override val id: String = "bitbucket-cloud"

    override val entries: List<SCMCatalogSource>
        get() = securityService.asAdmin {
            val workspaces = workspacesPerConfiguration()
            bitbucketCloudConfigurationService
                .configurations
                .flatMap { config ->
                    workspaces[config.name].orEmpty().flatMap { workspace ->
                        try {
                            entries(config, workspace)
                        } catch (any: Exception) {
                            logger.error("Cannot list the repositories of the $workspace workspace with the ${config.name} Bitbucket Cloud configuration", any)
                            emptyList()
                        }
                    }
                }
        }

    private fun workspacesPerConfiguration(): Map<String, Set<String>> =
        structureService.projectList
            .mapNotNull { project ->
                propertyService.getProperty(project, BitbucketCloudProjectConfigurationPropertyType::class.java).value
            }
            .groupBy({ it.configuration.name }, { it.workspace })
            .mapValues { (_, list) -> list.toSortedSet() }

    private fun entries(config: BitbucketCloudConfiguration, workspace: String): List<SCMCatalogSource> {
        val client = bitbucketCloudClientFactory.getBitbucketCloudClient(config)
        return client.getRepositories(workspace).mapNotNull { repo ->
            val lastModified = client.getRepositoryLastModified(repo)
            val creationDate = client.getRepositoryCreationDate(repo)
            lastModified?.let {
                SCMCatalogSource(
                    config = config.name,
                    repository = "$workspace/${repo.slug}",
                    repositoryPage = BitbucketCloudProjectConfigurationProperty(
                        configuration = config,
                        workspace = workspace,
                        repository = repo.slug,
                        indexationInterval = 0,
                        issueServiceConfigurationIdentifier = null,
                    ).repositoryUrl,
                    lastActivity = it,
                    createdAt = creationDate,
                )
            }
        }
    }

    override fun matches(entry: SCMCatalogEntry, project: Project): Boolean {
        val property: BitbucketCloudProjectConfigurationProperty? =
            propertyService.getProperty(project, BitbucketCloudProjectConfigurationPropertyType::class.java).value
        return property != null &&
                property.configuration.name == entry.config &&
                property.fullName == entry.repository
    }

    override fun toProjectName(scmRepository: String): String =
        scmRepository.substringAfter("/")

    override fun linkProjectToSCM(project: Project, entry: SCMCatalogEntry): Boolean {
        val config = bitbucketCloudConfigurationService.findConfiguration(entry.config) ?: return false
        val workspace = entry.repository.substringBefore("/", missingDelimiterValue = "")
        val repository = entry.repository.substringAfter("/")
        if (workspace.isBlank() || repository.isBlank()) return false
        propertyService.editProperty(
            project,
            BitbucketCloudProjectConfigurationPropertyType::class.java,
            BitbucketCloudProjectConfigurationProperty(
                configuration = config,
                workspace = workspace,
                repository = repository,
                indexationInterval = 0,
                issueServiceConfigurationIdentifier = null,
            )
        )
        return true
    }
}
