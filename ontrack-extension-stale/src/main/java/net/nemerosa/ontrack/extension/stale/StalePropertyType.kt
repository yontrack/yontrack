package net.nemerosa.ontrack.extension.stale

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.support.AbstractPropertyType
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.model.json.schema.toType
import net.nemerosa.ontrack.model.security.ProjectConfig
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import org.springframework.stereotype.Component
import java.util.*

@Component
class StalePropertyType(
    extensionFeature: StaleExtensionFeature,
) : AbstractPropertyType<StaleProperty>(extensionFeature) {

    override val name: String = "Stale branches"

    override val description: String = "Allows to disable or delete stale branches"

    override val supportedEntityTypes: Set<ProjectEntityType> = EnumSet.of(ProjectEntityType.PROJECT)

    override fun createConfigJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType =
        jsonTypeBuilder.toType(StaleProperty::class)

    override fun canEdit(entity: ProjectEntity, securityService: SecurityService): Boolean {
        return securityService.isProjectFunctionGranted(entity, ProjectConfig::class.java)
    }

    override fun canView(entity: ProjectEntity, securityService: SecurityService): Boolean = true


    override fun fromClient(node: JsonNode): StaleProperty {
        return fromStorage(node)
    }

    override fun fromStorage(node: JsonNode): StaleProperty {
        return parse(node, StaleProperty::class)
    }

    override fun replaceValue(value: StaleProperty, replacementFunction: (String) -> String): StaleProperty {
        return value
    }
}