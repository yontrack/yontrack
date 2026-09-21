package net.nemerosa.ontrack.extension.stale

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.config.extensions.CIConfigExtension
import net.nemerosa.ontrack.extension.support.AbstractExtension
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.model.json.schema.toType
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.structure.PropertyService
import org.springframework.stereotype.Component

@Component
class AutoDisablingBranchPatternsPropertyCIConfigExtension(
    staleExtensionFeature: StaleExtensionFeature,
    private val propertyService: PropertyService,
) :
    AbstractExtension(staleExtensionFeature),
    CIConfigExtension<AutoDisablingBranchPatternsPropertyCIConfigExtensionData> {

    override val id: String = "auto-disabling"

    override val projectEntityTypes: Set<ProjectEntityType> = setOf(ProjectEntityType.PROJECT)

    override fun parseData(data: JsonNode): AutoDisablingBranchPatternsPropertyCIConfigExtensionData = data.parse()

    override fun configure(
        entity: ProjectEntity,
        data: AutoDisablingBranchPatternsPropertyCIConfigExtensionData
    ) {
        val project  = entity as Project
        propertyService.editProperty(
            entity = project,
            propertyType = AutoDisablingBranchPatternsPropertyType::class.java,
            data = AutoDisablingBranchPatternsProperty(items = data.patterns)
        )
    }

    override fun createJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType =
        jsonTypeBuilder.toType(AutoDisablingBranchPatternsPropertyCIConfigExtensionData::class)

}