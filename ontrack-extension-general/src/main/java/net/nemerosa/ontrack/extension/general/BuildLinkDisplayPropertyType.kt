package net.nemerosa.ontrack.extension.general

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
class BuildLinkDisplayPropertyType(
        extensionFeature: GeneralExtensionFeature
) : AbstractPropertyType<BuildLinkDisplayProperty>(extensionFeature) {

    override val name: String = "Build link display options"

    override val description: String = "Configuration of display options for the build links towards this project."

    override val supportedEntityTypes: Set<ProjectEntityType> = EnumSet.of(ProjectEntityType.PROJECT)

    override fun createConfigJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType =
        jsonTypeBuilder.toType(BuildLinkDisplayProperty::class)

    override fun canEdit(entity: ProjectEntity, securityService: SecurityService): Boolean {
        return securityService.isProjectFunctionGranted(entity, ProjectConfig::class.java)
    }

    override fun canView(entity: ProjectEntity, securityService: SecurityService): Boolean {
        return true
    }

    override fun fromClient(node: JsonNode): BuildLinkDisplayProperty {
        return fromStorage(node)
    }

    override fun fromStorage(node: JsonNode): BuildLinkDisplayProperty {
        return parse(node, BuildLinkDisplayProperty::class)
    }

    override fun replaceValue(value: BuildLinkDisplayProperty, replacementFunction: (String) -> String): BuildLinkDisplayProperty {
        return value
    }
}
