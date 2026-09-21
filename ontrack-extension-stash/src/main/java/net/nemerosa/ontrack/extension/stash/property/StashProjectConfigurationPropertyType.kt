package net.nemerosa.ontrack.extension.stash.property

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.git.property.AbstractGitProjectConfigurationPropertyType
import net.nemerosa.ontrack.extension.stash.StashExtensionFeature
import net.nemerosa.ontrack.extension.stash.model.StashConfiguration
import net.nemerosa.ontrack.extension.stash.service.StashConfigurationService
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
class StashProjectConfigurationPropertyType(
    extensionFeature: StashExtensionFeature,
    private val configurationService: StashConfigurationService
) : AbstractGitProjectConfigurationPropertyType<StashProjectConfigurationProperty>(extensionFeature),
    ConfigurationPropertyType<StashConfiguration, StashProjectConfigurationProperty> {
    override val name: String = "Bitbucket Server configuration"

    override val description: String = "Associates the project with a Bitbucket Server repository"

    override val supportedEntityTypes: Set<ProjectEntityType> = EnumSet.of(ProjectEntityType.PROJECT)

    override fun createConfigJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType =
        jsonTypeBuilder.toType(StashProjectConfigurationProperty::class)

    override fun canEdit(entity: ProjectEntity, securityService: SecurityService): Boolean {
        return securityService.isProjectFunctionGranted(entity.projectId(), ProjectConfig::class.java)
    }

    override fun canView(entity: ProjectEntity, securityService: SecurityService): Boolean {
        return true
    }

    override fun fromClient(node: JsonNode): StashProjectConfigurationProperty {
        return fromStorage(node)
    }

    override fun fromStorage(node: JsonNode): StashProjectConfigurationProperty {
        val configurationName = node.path("configuration").asText()
        // Looks the configuration up
        val configuration = configurationService.getConfiguration(configurationName)
        // OK
        return StashProjectConfigurationProperty(
            configuration = configuration,
            project = node.path("project").asText(),
            repository = node.path("repository").asText(),
            indexationInterval = node.path("indexationInterval").asInt(),
            issueServiceConfigurationIdentifier = node.getTextField("issueServiceConfigurationIdentifier")
        )
    }

    override fun forStorage(value: StashProjectConfigurationProperty): JsonNode {
        return mapOf(
            "configuration" to value.configuration.name,
            "project" to value.project,
            "repository" to value.repository,
            "indexationInterval" to value.indexationInterval,
            "issueServiceConfigurationIdentifier" to value.issueServiceConfigurationIdentifier,
        ).asJson()
    }

    override fun replaceValue(
        value: StashProjectConfigurationProperty,
        replacementFunction: (String) -> String
    ): StashProjectConfigurationProperty {
        return StashProjectConfigurationProperty(
            value.configuration,
            replacementFunction(value.project),
            replacementFunction(value.repository),
            value.indexationInterval,
            value.issueServiceConfigurationIdentifier
        )
    }
}
