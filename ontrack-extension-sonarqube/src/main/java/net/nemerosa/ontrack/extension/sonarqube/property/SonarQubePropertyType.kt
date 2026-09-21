package net.nemerosa.ontrack.extension.sonarqube.property

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.sonarqube.SonarQubeExtensionFeature
import net.nemerosa.ontrack.extension.sonarqube.configuration.SonarQubeConfiguration
import net.nemerosa.ontrack.extension.sonarqube.configuration.SonarQubeConfigurationService
import net.nemerosa.ontrack.extension.support.AbstractPropertyType
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.getBooleanField
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

@Component
class SonarQubePropertyType(
    extensionFeature: SonarQubeExtensionFeature,
    private val configurationService: SonarQubeConfigurationService
) : AbstractPropertyType<SonarQubeProperty>(extensionFeature),
    ConfigurationPropertyType<SonarQubeConfiguration, SonarQubeProperty> {

    override val name: String = "SonarQube"

    override val description: String = "Association with a SonarQube project."

    override val supportedEntityTypes: Set<ProjectEntityType> =
        setOf(ProjectEntityType.PROJECT)

    override fun createConfigJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType =
        jsonTypeBuilder.toType(SonarQubeProperty::class)

    override fun canEdit(entity: ProjectEntity, securityService: SecurityService): Boolean =
        securityService.isProjectFunctionGranted(entity, ProjectConfig::class.java)

    override fun canView(entity: ProjectEntity, securityService: SecurityService): Boolean = true

    override fun fromClient(node: JsonNode): SonarQubeProperty = fromStorage(node)

    override fun fromStorage(node: JsonNode): SonarQubeProperty {
        val configurationName = node.path("configuration").asText()
        // Looks the configuration up
        val configuration = configurationService.getConfiguration(configurationName)
        // OK
        return SonarQubeProperty(
            configuration = configuration,
            key = node.path("key").asText(),
            validationStamp = node.path("validationStamp").asText().ifBlank { SonarQubeProperty.DEFAULT_VALIDATION_STAMP },
            measures = node.path("measures").values().map { it.asText() },
            override = node.path("override").asBoolean(),
            branchModel = node.path("branchModel").asBoolean(),
            branchPattern = node.getTextField("branchPattern"),
            validationMetrics = node.getBooleanField(SonarQubeProperty::validationMetrics.name) ?: true,
        )
    }

    override fun forStorage(value: SonarQubeProperty): JsonNode =
        mapOf(
            "configuration" to value.configuration.name,
            "key" to value.key,
            "validationStamp" to value.validationStamp,
            "measures" to value.measures,
            "override" to value.override,
            "branchModel" to value.branchModel,
            "branchPattern" to value.branchPattern,
            SonarQubeProperty::validationMetrics.name to value.validationMetrics,
        ).asJson()

    override fun replaceValue(value: SonarQubeProperty, replacementFunction: (String) -> String) =
        SonarQubeProperty(
            value.configuration,
            replacementFunction(value.key),
            replacementFunction(value.validationStamp),
            value.measures,
            value.override,
            value.branchModel,
            value.branchPattern,
            value.validationMetrics,
        )
}