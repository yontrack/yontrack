package net.nemerosa.ontrack.extension.scm.service

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.extension.scm.changelog.SCMChangeLogEnabled
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
@APIDescription("Gets the full SCM commit hash associated with a build. Renders an empty string if there is none.")
@DocumentationExampleCode("${'$'}{build.scmCommit}")
@DocumentationIgnore
class SCMCommitTemplatingSource(
    private val scmDetector: SCMDetector,
) : AbstractTemplatingSource(
    field = "scmCommit",
    type = ProjectEntityType.BUILD,
) {

    override fun render(entity: ProjectEntity, config: TemplatingSourceConfig, renderer: EventRenderer): String =
        if (entity is Build) {
            val scm = scmDetector.getSCM(entity.project)
            if (scm is SCMChangeLogEnabled) {
                scm.getBuildCommit(entity) ?: ""
            } else {
                ""
            }
        } else {
            ""
        }

}
