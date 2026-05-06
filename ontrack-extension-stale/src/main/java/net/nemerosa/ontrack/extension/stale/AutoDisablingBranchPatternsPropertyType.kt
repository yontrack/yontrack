package net.nemerosa.ontrack.extension.stale

import com.fasterxml.jackson.databind.JsonNode
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
class AutoDisablingBranchPatternsPropertyType(
    staleExtensionFeature: StaleExtensionFeature,
) : AbstractPropertyType<AutoDisablingBranchPatternsProperty>(
    staleExtensionFeature
) {

    override val name: String = "Auto-disabling of branches based on patterns"

    override val description: String =
        "Given a list of patterns and their behaviour, allows the disabling of branches based on their Ontrack names."

    override val supportedEntityTypes = setOf(ProjectEntityType.PROJECT)

    override fun createConfigJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType =
        jsonTypeBuilder.toType(AutoDisablingBranchPatternsProperty::class)

    override fun canEdit(entity: ProjectEntity, securityService: SecurityService): Boolean =
        securityService.isProjectFunctionGranted(entity, ProjectConfig::class.java)

    override fun canView(entity: ProjectEntity, securityService: SecurityService): Boolean = true

    override fun fromClient(node: JsonNode): AutoDisablingBranchPatternsProperty =
        fromStorage(node)

    override fun fromStorage(node: JsonNode): AutoDisablingBranchPatternsProperty =
        node.parse<AutoDisablingBranchPatternsProperty>()

    override fun replaceValue(
        value: AutoDisablingBranchPatternsProperty,
        replacementFunction: (String) -> String,
    ): AutoDisablingBranchPatternsProperty = value
}