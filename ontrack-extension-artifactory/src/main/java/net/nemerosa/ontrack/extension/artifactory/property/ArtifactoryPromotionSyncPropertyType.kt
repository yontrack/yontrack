package net.nemerosa.ontrack.extension.artifactory.property

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.artifactory.ArtifactoryExtensionFeature
import net.nemerosa.ontrack.extension.artifactory.configuration.ArtifactoryConfiguration
import net.nemerosa.ontrack.extension.artifactory.configuration.ArtifactoryConfigurationService
import net.nemerosa.ontrack.extension.support.AbstractPropertyType
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

@Component
class ArtifactoryPromotionSyncPropertyType(
    extensionFeature: ArtifactoryExtensionFeature,
    private val configurationService: ArtifactoryConfigurationService
) : AbstractPropertyType<ArtifactoryPromotionSyncProperty>(extensionFeature),
    ConfigurationPropertyType<ArtifactoryConfiguration, ArtifactoryPromotionSyncProperty> {

    override val name: String = "Artifactory promotion sync"

    override val description: String = "Synchronisation of the promotions with Artifactory build statuses"

    override val supportedEntityTypes = setOf(ProjectEntityType.BRANCH)

    override fun createConfigJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType =
        jsonTypeBuilder.toType(ArtifactoryPromotionSyncProperty::class)

    override fun canEdit(entity: ProjectEntity, securityService: SecurityService): Boolean {
        return securityService.isProjectFunctionGranted(entity.projectId(), ProjectConfig::class.java)
    }

    override fun canView(entity: ProjectEntity, securityService: SecurityService): Boolean {
        return securityService.isProjectFunctionGranted(entity.projectId(), ProjectConfig::class.java)
    }

    override fun forStorage(value: ArtifactoryPromotionSyncProperty): JsonNode =
        mapOf(
            "configuration" to value.configuration.name,
            "buildName" to value.buildName,
            "buildNameFilter" to value.buildNameFilter,
            "interval" to value.interval,
        ).asJson()

    override fun fromClient(node: JsonNode): ArtifactoryPromotionSyncProperty {
        return fromStorage(node)
    }

    override fun fromStorage(node: JsonNode): ArtifactoryPromotionSyncProperty {
        val configurationName = node.path("configuration").asText()
        val buildName = node.path("buildName").asText()
        val buildNameFilter = node.path("buildNameFilter").asText()
        var interval = node.path("interval").asInt()
        // Looks the configuration up
        val configuration = configurationService.getConfiguration(configurationName)
        // Validates the project path
        validateNotBlank(buildName, "The build name must not be empty")
        // Validates the interval
        if (interval < 0) interval = 0
        // OK
        return ArtifactoryPromotionSyncProperty(
            configuration,
            buildName,
            buildNameFilter,
            interval
        )
    }

    override fun replaceValue(
        value: ArtifactoryPromotionSyncProperty,
        replacementFunction: (String) -> String,
    ): ArtifactoryPromotionSyncProperty {
        return ArtifactoryPromotionSyncProperty(
            value.configuration,
            replacementFunction(value.buildName),
            replacementFunction(value.buildNameFilter),
            value.interval
        )
    }
}
