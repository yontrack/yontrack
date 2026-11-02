package net.nemerosa.ontrack.extension.general

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
import tools.jackson.databind.JsonNode

@Component
class NotGrantedMedalsPropertyType(
    extensionFeature: GeneralExtensionFeature,
) : AbstractPropertyType<NotGrantedMedalsProperty>(extensionFeature) {

    override val name: String = "Not-granted medals"

    override val description: String =
        "Whether the branch builds view shows a medal for each auto-promoted level a build does not have yet."

    /**
     * Project and branch: below, it would be an override per promotion level, which is out of the design.
     */
    override val supportedEntityTypes: Set<ProjectEntityType> =
        setOf(
            ProjectEntityType.PROJECT,
            ProjectEntityType.BRANCH,
        )

    override fun createConfigJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType =
        jsonTypeBuilder.toType(NotGrantedMedalsProperty::class)

    override fun canEdit(entity: ProjectEntity, securityService: SecurityService): Boolean =
        securityService.isProjectFunctionGranted(entity, ProjectConfig::class.java)

    override fun canView(entity: ProjectEntity, securityService: SecurityService): Boolean =
        true

    override fun fromClient(node: JsonNode): NotGrantedMedalsProperty = fromStorage(node)

    override fun fromStorage(node: JsonNode): NotGrantedMedalsProperty = node.parse()

    override fun replaceValue(
        value: NotGrantedMedalsProperty,
        replacementFunction: (String) -> String,
    ): NotGrantedMedalsProperty = value
}
