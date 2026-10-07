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
import org.springframework.stereotype.Component

/**
 * *Agents admitted* on a promotion level: without it, the agent policy refuses any promotion by an
 * agent to this level.
 *
 * Never rename this class: its FQCN is the storage ID of the property.
 */
@Component
class AgentsAdmittedPropertyType(
    extensionFeature: GeneralExtensionFeature,
) : AbstractPropertyType<AgentsAdmittedProperty>(extensionFeature) {

    override val name: String = "Agents admitted"

    override val description: String =
        "Admits registered agents to this promotion level: an agent may promote a build to it, provided its owner may. " +
                "Without it, an agent may not promote to this level. Auto-promotion is not concerned."

    override val supportedEntityTypes: Set<ProjectEntityType> = setOf(ProjectEntityType.PROMOTION_LEVEL)

    override fun createConfigJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType =
        jsonTypeBuilder.toType(AgentsAdmittedProperty::class)

    override fun canEdit(entity: ProjectEntity, securityService: SecurityService): Boolean =
        securityService.isProjectFunctionGranted(entity, ProjectConfig::class.java)

    override fun canView(entity: ProjectEntity, securityService: SecurityService): Boolean = true

    override fun fromClient(node: JsonNode): AgentsAdmittedProperty = fromStorage(node)

    override fun fromStorage(node: JsonNode): AgentsAdmittedProperty = node.parse()

    override fun replaceValue(
        value: AgentsAdmittedProperty,
        replacementFunction: (String) -> String
    ): AgentsAdmittedProperty = value
}
