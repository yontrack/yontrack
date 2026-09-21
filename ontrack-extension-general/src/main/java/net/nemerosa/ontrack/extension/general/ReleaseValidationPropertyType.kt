package net.nemerosa.ontrack.extension.general

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.support.AbstractPropertyType
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.model.json.schema.toType
import net.nemerosa.ontrack.model.security.ProjectConfig
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.structure.PropertySearchArguments
import org.springframework.stereotype.Component

@Component
class ReleaseValidationPropertyType(
    extensionFeature: GeneralExtensionFeature,
) : AbstractPropertyType<ReleaseValidationProperty>(extensionFeature) {

    override val name: String = "Validation on release/label"

    override val description: String = "When set, adding a release/label on a build will also validate this build."

    override val supportedEntityTypes: Set<ProjectEntityType> = setOf(ProjectEntityType.BRANCH)

    override fun createConfigJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType =
        jsonTypeBuilder.toType(ReleaseValidationProperty::class)

    override fun canEdit(entity: ProjectEntity, securityService: SecurityService): Boolean =
        securityService.isProjectFunctionGranted(entity, ProjectConfig::class.java)

    override fun canView(entity: ProjectEntity, securityService: SecurityService): Boolean = true

    override fun fromClient(node: JsonNode): ReleaseValidationProperty = fromStorage(node)

    override fun fromStorage(node: JsonNode): ReleaseValidationProperty = node.parse()

    @Deprecated("Will be removed in V5")
    override fun replaceValue(
        value: ReleaseValidationProperty,
        replacementFunction: (String) -> String,
    ) = ReleaseValidationProperty(
        validation = replacementFunction(value.validation)
    )

    override fun containsValue(value: ReleaseValidationProperty, propertyValue: String): Boolean =
        value.validation == propertyValue

    override fun getSearchArguments(token: String): PropertySearchArguments? =
        if (token.isNotBlank()) {
            PropertySearchArguments(
                null,
                "UPPER(pp.json->>'validation') = UPPER(:token)",
                mapOf("token" to token)
            )
        } else {
            null
        }
}