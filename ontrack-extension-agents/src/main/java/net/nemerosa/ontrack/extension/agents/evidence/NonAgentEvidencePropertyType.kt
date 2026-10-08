package net.nemerosa.ontrack.extension.agents.evidence

import net.nemerosa.ontrack.extension.agents.AgentsExtensionFeature
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

/**
 * *Evidence from non-agents only*, on a validation stamp: agents produce configuration and rules,
 * never numbers. [NonAgentEvidenceCheck] applies it while the licence allows the agent governance;
 * without it, the property stays, and does nothing.
 *
 * Never rename this class: its FQCN is the storage ID of the property.
 */
@Component
class NonAgentEvidencePropertyType(
    extensionFeature: AgentsExtensionFeature,
) : AbstractPropertyType<NonAgentEvidenceProperty>(extensionFeature) {

    override val name: String = "Evidence from non-agents only"

    override val description: String =
        "Evidence on this validation stamp must come from a non-agent actor: an agent may neither create a validation run on it nor change the status of one of its runs. Needs the licensed feature \"Agent governance\"."

    override val supportedEntityTypes: Set<ProjectEntityType> = setOf(ProjectEntityType.VALIDATION_STAMP)

    override fun createConfigJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType =
        jsonTypeBuilder.toType(NonAgentEvidenceProperty::class)

    override fun canEdit(entity: ProjectEntity, securityService: SecurityService): Boolean =
        securityService.isProjectFunctionGranted(entity, ProjectConfig::class.java)

    override fun canView(entity: ProjectEntity, securityService: SecurityService): Boolean = true

    override fun fromClient(node: JsonNode): NonAgentEvidenceProperty = fromStorage(node)

    override fun fromStorage(node: JsonNode): NonAgentEvidenceProperty = node.parse()

    override fun replaceValue(
        value: NonAgentEvidenceProperty,
        replacementFunction: (String) -> String,
    ): NonAgentEvidenceProperty = value
}
