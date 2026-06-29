package net.nemerosa.ontrack.extension.general

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.model.docs.Documentation
import net.nemerosa.ontrack.model.docs.DocumentationExampleCode
import net.nemerosa.ontrack.model.events.EventRenderer
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.structure.PropertyService
import net.nemerosa.ontrack.model.templating.AbstractTemplatingSource
import net.nemerosa.ontrack.model.templating.TemplatingSourceConfig
import net.nemerosa.ontrack.model.templating.getRequiredString
import org.springframework.stereotype.Component

@Component
@APIDescription("Gets some meta information from a project entity.")
@Documentation(MetaInfoPropertyTemplatingSourceConfig::class)
@DocumentationExampleCode($$"${build.meta?name=key}")
class MetaInfoPropertyTemplatingSource(
    private val propertyService: PropertyService,
) : AbstractTemplatingSource(
    field = "meta",
    types = ProjectEntityType.entries.toSet(),
) {
    override fun render(entity: ProjectEntity, config: TemplatingSourceConfig, renderer: EventRenderer): String {
        val name = config.getRequiredString(MetaInfoPropertyTemplatingSourceConfig::name.name)
        val category = config.getString(MetaInfoPropertyTemplatingSourceConfig::category.name)
        val error = config.getBoolean(MetaInfoPropertyTemplatingSourceConfig::error.name)
        val link = config.getBoolean(MetaInfoPropertyTemplatingSourceConfig::link.name)

        val property = propertyService.getPropertyValue(entity, MetaInfoPropertyType::class.java)
        val item = property?.findMetaInfoItems(
            name = name,
            category = category,
        )?.firstOrNull()

        return if (item != null) {
            if (link && !item.link.isNullOrBlank() && !item.value.isNullOrBlank()) {
                renderer.renderLink(item.value, item.link)
            } else {
                item.value ?: ""
            }
        } else if (error) {
            throw MetaInfoPropertyTemplatingSourceMissingKeyException(name)
        } else {
            ""
        }
    }
}