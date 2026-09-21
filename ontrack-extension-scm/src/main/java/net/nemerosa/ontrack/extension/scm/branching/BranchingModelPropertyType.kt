package net.nemerosa.ontrack.extension.scm.branching

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.scm.SCMExtensionFeature
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
class BranchingModelPropertyType(
    extensionFeature: SCMExtensionFeature,
) : AbstractPropertyType<BranchingModelProperty>(extensionFeature) {
    override val name: String = "Branching Model"

    override val description: String =
        "Defines the branching model used by a project"

    override val supportedEntityTypes =
        setOf(ProjectEntityType.PROJECT)

    override fun createConfigJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType =
        jsonTypeBuilder.toType(BranchingModelProperty::class)

    override fun canEdit(entity: ProjectEntity, securityService: SecurityService): Boolean =
        securityService.isProjectFunctionGranted(entity, ProjectConfig::class.java)

    override fun canView(entity: ProjectEntity, securityService: SecurityService): Boolean =
        true

    override fun fromClient(node: JsonNode): BranchingModelProperty {
        return fromStorage(node)
    }

    override fun fromStorage(node: JsonNode): BranchingModelProperty {
        return node.parse()
    }

    override fun replaceValue(value: BranchingModelProperty, replacementFunction: (String) -> String) = value
}