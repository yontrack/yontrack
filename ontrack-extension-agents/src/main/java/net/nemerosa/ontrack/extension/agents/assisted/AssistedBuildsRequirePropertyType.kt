package net.nemerosa.ontrack.extension.agents.assisted

import net.nemerosa.ontrack.extension.agents.AgentsExtensionFeature
import net.nemerosa.ontrack.extension.support.AbstractPropertyType
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.exceptions.PropertyValidationException
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
 * *Assisted builds require*, on a promotion level: the validation stamps an assisted build must pass
 * before being promoted to the level. [AssistedBuildsRequireCheckExtension] applies it, while the
 * licence allows the agent governance; without it, the property stays, and does nothing.
 *
 * Never rename this class: its FQCN is the storage ID of the property.
 */
@Component
class AssistedBuildsRequirePropertyType(
    extensionFeature: AgentsExtensionFeature,
) : AbstractPropertyType<AssistedBuildsRequireProperty>(extensionFeature) {

    override val name: String = "Assisted builds require"

    override val description: String =
        "If the build is assisted (its commits were written with coding agents), these validation stamps must pass before it is promoted to this level. An unknown assisted change counts as assisted. Needs the licensed feature \"Agent governance\"."

    override val supportedEntityTypes: Set<ProjectEntityType> = setOf(ProjectEntityType.PROMOTION_LEVEL)

    override fun createConfigJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType =
        jsonTypeBuilder.toType(AssistedBuildsRequireProperty::class)

    override fun canEdit(entity: ProjectEntity, securityService: SecurityService): Boolean =
        securityService.isProjectFunctionGranted(entity, ProjectConfig::class.java)

    override fun canView(entity: ProjectEntity, securityService: SecurityService): Boolean = true

    override fun fromClient(node: JsonNode): AssistedBuildsRequireProperty {
        val value = try {
            node.parse<AssistedBuildsRequireProperty>()
        } catch (any: Exception) {
            throw PropertyValidationException("Cannot parse the validation stamps required of assisted builds: ${any.message}")
        }
        return AssistedBuildsRequireProperty.normalised(value)
    }

    override fun fromStorage(node: JsonNode): AssistedBuildsRequireProperty = node.parse()

    override fun replaceValue(
        value: AssistedBuildsRequireProperty,
        replacementFunction: (String) -> String,
    ): AssistedBuildsRequireProperty = value
}
