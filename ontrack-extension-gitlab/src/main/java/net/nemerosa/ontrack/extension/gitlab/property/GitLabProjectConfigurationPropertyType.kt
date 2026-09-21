package net.nemerosa.ontrack.extension.gitlab.property

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.git.property.AbstractGitProjectConfigurationPropertyType
import net.nemerosa.ontrack.extension.gitlab.GitLabExtensionFeature
import net.nemerosa.ontrack.extension.gitlab.model.GitLabConfiguration
import net.nemerosa.ontrack.extension.gitlab.service.GitLabConfigurationService
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
class GitLabProjectConfigurationPropertyType(
    extensionFeature: GitLabExtensionFeature,
    private val configurationService: GitLabConfigurationService
) : AbstractGitProjectConfigurationPropertyType<GitLabProjectConfigurationProperty>(extensionFeature),
    ConfigurationPropertyType<GitLabConfiguration, GitLabProjectConfigurationProperty> {
    override val name: String
        get() = "GitLab configuration"

    override val description: String
        get() = "Associates the project with a GitLab repository"

    override val supportedEntityTypes: Set<ProjectEntityType>
        get() = EnumSet.of(ProjectEntityType.PROJECT)

    override fun createConfigJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType =
        jsonTypeBuilder.toType(GitLabProjectConfigurationProperty::class)

    override fun canEdit(entity: ProjectEntity, securityService: SecurityService): Boolean {
        return securityService.isProjectFunctionGranted(entity.projectId(), ProjectConfig::class.java)
    }

    override fun canView(entity: ProjectEntity, securityService: SecurityService): Boolean {
        return true
    }

    override fun fromClient(node: JsonNode): GitLabProjectConfigurationProperty {
        return fromStorage(node)
    }

    override fun fromStorage(node: JsonNode): GitLabProjectConfigurationProperty {
        val configurationName = node.path("configuration").asText()
        // Looks the configuration up
        val configuration = configurationService.getConfiguration(configurationName)
        // OK
        return GitLabProjectConfigurationProperty(
            configuration = configuration,
            issueServiceConfigurationIdentifier = node.getTextField("issueServiceConfigurationIdentifier"),
            repository = node.path("repository").asText(),
            indexationInterval = node.path("indexationInterval").asInt()
        )
    }

    override fun forStorage(value: GitLabProjectConfigurationProperty): JsonNode =
        mapOf(
            "configuration" to value.configuration.name,
            "repository" to value.repository,
            "indexationInterval" to value.indexationInterval,
            "issueServiceConfigurationIdentifier" to value.issueServiceConfigurationIdentifier,
        ).asJson()

    override fun replaceValue(
        value: GitLabProjectConfigurationProperty,
        replacementFunction: (String) -> String
    ): GitLabProjectConfigurationProperty {
        return GitLabProjectConfigurationProperty(
            value.configuration,
            value.issueServiceConfigurationIdentifier,
            replacementFunction(value.repository),
            value.indexationInterval
        )
    }
}
