package net.nemerosa.ontrack.extension.av.project

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.av.AutoVersioningExtensionFeature
import net.nemerosa.ontrack.extension.support.AbstractPropertyType
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.model.json.schema.toType
import net.nemerosa.ontrack.model.security.ProjectConfig
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import org.springframework.stereotype.Component

@Component
class AutoVersioningProjectPropertyType(
    extensionFeature: AutoVersioningExtensionFeature,
) : AbstractPropertyType<AutoVersioningProjectProperty>(
    extensionFeature
) {
    override val name: String = "Auto-versioning"

    override val description: String = "Auto-versioning rules at project level"

    override val supportedEntityTypes: Set<ProjectEntityType> = setOf(ProjectEntityType.PROJECT)

    override fun createConfigJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType =
        jsonTypeBuilder.toType(AutoVersioningProjectProperty::class)

    override fun canEdit(entity: ProjectEntity, securityService: SecurityService): Boolean =
        securityService.isProjectFunctionGranted(entity, ProjectConfig::class.java)

    override fun canView(entity: ProjectEntity, securityService: SecurityService): Boolean = true

    override fun fromClient(node: JsonNode) = fromStorage(node)

    override fun fromStorage(node: JsonNode) = node.parse<AutoVersioningProjectProperty>()

    override fun replaceValue(
        value: AutoVersioningProjectProperty,
        replacementFunction: (String) -> String,
    ): AutoVersioningProjectProperty = value

}