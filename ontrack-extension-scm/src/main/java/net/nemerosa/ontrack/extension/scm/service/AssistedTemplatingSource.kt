package net.nemerosa.ontrack.extension.scm.service

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.extension.scm.changelog.assistants.AssistedChangeBasis
import net.nemerosa.ontrack.extension.scm.changelog.assistants.AssistedChangeService
import net.nemerosa.ontrack.model.docs.DocumentationExampleCode
import net.nemerosa.ontrack.model.docs.DocumentationIgnore
import net.nemerosa.ontrack.model.events.EventRenderer
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.templating.AbstractTemplatingSource
import net.nemerosa.ontrack.model.templating.TemplatingSourceConfig
import org.springframework.stereotype.Component

@Component
@APIDescription("Whether the change of a build is agent-assisted: `true`, `false`, or `unknown` when its assisted change has not been computed, or could not be.")
@DocumentationExampleCode("${'$'}{build.assisted}")
@DocumentationIgnore
class AssistedTemplatingSource(
    @DocumentationIgnore
    private val assistedChangeService: AssistedChangeService,
) : AbstractTemplatingSource(
    field = "assisted",
    type = ProjectEntityType.BUILD,
) {

    override fun render(entity: ProjectEntity, config: TemplatingSourceConfig, renderer: EventRenderer): String {
        if (entity !is Build) return ""
        val value = assistedChangeService.getAssistedChange(entity)
        return when {
            value == null || value.basis == AssistedChangeBasis.UNKNOWN -> UNKNOWN
            else -> value.assisted.toString()
        }
    }

    companion object {
        /**
         * Rendering of an unknown assisted status
         */
        const val UNKNOWN = "unknown"
    }
}
