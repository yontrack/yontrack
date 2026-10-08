package net.nemerosa.ontrack.extension.scm.service

import net.nemerosa.ontrack.common.api.APIDescription
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
@APIDescription("Names of the assistants (agent kinds) of the change of a build, sorted and comma-separated. Renders an empty string if there is none, or if its assisted change is unknown.")
@DocumentationExampleCode("${'$'}{build.assistants}")
@DocumentationIgnore
class AssistantsTemplatingSource(
    @DocumentationIgnore
    private val assistedChangeService: AssistedChangeService,
) : AbstractTemplatingSource(
    field = "assistants",
    type = ProjectEntityType.BUILD,
) {

    override fun render(entity: ProjectEntity, config: TemplatingSourceConfig, renderer: EventRenderer): String =
        if (entity is Build) {
            assistedChangeService.getAssistedChange(entity)?.assistants?.joinToString(", ") ?: ""
        } else {
            ""
        }
}
