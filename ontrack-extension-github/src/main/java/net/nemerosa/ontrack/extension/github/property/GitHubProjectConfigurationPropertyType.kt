package net.nemerosa.ontrack.extension.github.property

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.git.property.AbstractGitProjectConfigurationPropertyType
import net.nemerosa.ontrack.extension.github.GitHubExtensionFeature
import net.nemerosa.ontrack.extension.github.model.GitHubEngineConfiguration
import net.nemerosa.ontrack.extension.github.service.GitHubConfigurationService
import net.nemerosa.ontrack.extension.issues.IssueServiceRegistry
import net.nemerosa.ontrack.extension.issues.model.IssueServiceConfigurationIdentifierNotFoundException
import net.nemerosa.ontrack.extension.issues.model.IssueServiceConfigurationRepresentation.Companion.isSelf
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.getTextField
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.model.json.schema.toType
import net.nemerosa.ontrack.model.security.ProjectConfig
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.support.ConfigurationPropertyType
import org.springframework.stereotype.Component
import java.util.*

@Component
class GitHubProjectConfigurationPropertyType(
    extensionFeature: GitHubExtensionFeature,
    private val configurationService: GitHubConfigurationService,
    private val issueServiceRegistry: IssueServiceRegistry
) : AbstractGitProjectConfigurationPropertyType<GitHubProjectConfigurationProperty>(extensionFeature),
    ConfigurationPropertyType<GitHubEngineConfiguration, GitHubProjectConfigurationProperty> {

    override val name: String = "GitHub configuration"

    override val description: String = "Associates the project with a GitHub repository"

    override val supportedEntityTypes: Set<ProjectEntityType> = EnumSet.of(ProjectEntityType.PROJECT)

    override fun createConfigJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType =
        jsonTypeBuilder.toType(GitHubProjectConfigurationProperty::class)

    override fun canEdit(entity: ProjectEntity, securityService: SecurityService): Boolean {
        return securityService.isProjectFunctionGranted(entity.projectId(), ProjectConfig::class.java)
    }

    override fun canView(entity: ProjectEntity, securityService: SecurityService): Boolean {
        return true
    }

    override fun fromClient(node: JsonNode): GitHubProjectConfigurationProperty {
        val property = fromStorage(node)
        // Checks the issue service configuration
        val issueServiceConfigurationIdentifier = property.issueServiceConfigurationIdentifier
        if (!issueServiceConfigurationIdentifier.isNullOrBlank() && !isSelf(issueServiceConfigurationIdentifier)) {
            val configuredIssueService = issueServiceRegistry.getConfiguredIssueService(
                issueServiceConfigurationIdentifier
            )
            if (configuredIssueService == null) {
                throw IssueServiceConfigurationIdentifierNotFoundException(issueServiceConfigurationIdentifier)
            }
        }
        // OK
        return property
    }

    override fun fromStorage(node: JsonNode): GitHubProjectConfigurationProperty {
        val configurationName = node.path("configuration").asText()
        // Looks the configuration up
        val configuration = configurationService.getConfiguration(configurationName)
        // OK
        return GitHubProjectConfigurationProperty(
            configuration = configuration,
            repository = node.path("repository").asText(),
            indexationInterval = node.path("indexationInterval").asInt(),
            issueServiceConfigurationIdentifier = node.getTextField("issueServiceConfigurationIdentifier"),
        )
    }

    override fun forStorage(value: GitHubProjectConfigurationProperty): JsonNode {
        return mapOf(
            "configuration" to value.configuration.name,
            "repository" to value.repository,
            "indexationInterval" to value.indexationInterval,
            "issueServiceConfigurationIdentifier" to value.issueServiceConfigurationIdentifier,
        ).asJson()
    }

    override fun replaceValue(
        value: GitHubProjectConfigurationProperty,
        replacementFunction: (String) -> String
    ): GitHubProjectConfigurationProperty {
        return GitHubProjectConfigurationProperty(
            configuration = value.configuration,
            repository = replacementFunction(value.repository),
            indexationInterval = value.indexationInterval,
            issueServiceConfigurationIdentifier = value.issueServiceConfigurationIdentifier
        )
    }
}
