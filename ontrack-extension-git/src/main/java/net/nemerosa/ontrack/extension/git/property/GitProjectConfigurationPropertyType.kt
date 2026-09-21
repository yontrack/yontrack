package net.nemerosa.ontrack.extension.git.property

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.git.GitExtensionFeature
import net.nemerosa.ontrack.extension.git.model.BasicGitConfiguration
import net.nemerosa.ontrack.extension.git.service.GitConfigurationService
import net.nemerosa.ontrack.json.asJson
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
@Deprecated("Will be removed in V5. Pure Git configuration won't be supported any longer.")
class GitProjectConfigurationPropertyType(
    extensionFeature: GitExtensionFeature,
    private val configurationService: GitConfigurationService
) : AbstractGitProjectConfigurationPropertyType<GitProjectConfigurationProperty>(extensionFeature),
    ConfigurationPropertyType<BasicGitConfiguration, GitProjectConfigurationProperty> {

    override val name: String = "Git configuration"

    override val description: String = "Associates the project with a Git repository"

    override val supportedEntityTypes: Set<ProjectEntityType> = EnumSet.of(ProjectEntityType.PROJECT)

    override fun createConfigJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType =
        jsonTypeBuilder.toType(GitProjectConfigurationProperty::class)

    override fun canEdit(entity: ProjectEntity, securityService: SecurityService): Boolean {
        return securityService.isProjectFunctionGranted(entity.projectId(), ProjectConfig::class.java)
    }

    override fun canView(entity: ProjectEntity, securityService: SecurityService): Boolean {
        return true
    }

    override fun fromClient(node: JsonNode): GitProjectConfigurationProperty {
        return fromStorage(node)
    }

    override fun fromStorage(node: JsonNode): GitProjectConfigurationProperty {
        val configurationName = node.path("configuration").asText()
        // Looks the configuration up
        val configuration = configurationService.getConfiguration(configurationName)
        // OK
        return GitProjectConfigurationProperty(
            configuration
        )
    }

    override fun forStorage(value: GitProjectConfigurationProperty): JsonNode =
        mapOf("configuration" to value.configuration.name)
            .asJson()

    override fun replaceValue(
        value: GitProjectConfigurationProperty,
        replacementFunction: (String) -> String
    ): GitProjectConfigurationProperty {
        return GitProjectConfigurationProperty(
            value.configuration
        )
    }
}
