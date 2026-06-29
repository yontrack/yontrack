package net.nemerosa.ontrack.service.templating

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.model.docs.Documentation
import net.nemerosa.ontrack.model.docs.DocumentationExampleCode
import net.nemerosa.ontrack.model.events.EventRenderer
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.structure.PromotionRun
import net.nemerosa.ontrack.model.templating.TemplatingSource
import net.nemerosa.ontrack.model.templating.TemplatingSourceConfig
import net.nemerosa.ontrack.model.templating.getRequiredString
import org.springframework.stereotype.Component

@Component
@APIDescription("Gets the value of a field of a promotion run.")
@Documentation(PromotionRunFieldTemplatingSourceConfig::class)
@DocumentationExampleCode($$"${promotionRun.field?name=key}")
class PromotionRunFieldTemplatingSource: TemplatingSource {

    override val types: Set<ProjectEntityType> = setOf(ProjectEntityType.PROMOTION_RUN)
    override val field: String = "field"

    override fun render(
        entity: ProjectEntity,
        config: TemplatingSourceConfig,
        renderer: EventRenderer
    ): String {
        val name = config.getRequiredString(PromotionRunFieldTemplatingSourceConfig::name.name)
        val run = entity as PromotionRun
        return run.fieldValues.find { it.name == name }?.value?.asText() ?: ""
    }
}