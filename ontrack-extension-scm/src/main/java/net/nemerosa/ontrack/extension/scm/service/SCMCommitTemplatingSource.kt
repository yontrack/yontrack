package net.nemerosa.ontrack.extension.scm.service

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.extension.scm.changelog.SCMChangeLogEnabled
import net.nemerosa.ontrack.extension.scm.changelog.assistants.SCMCommitAssistantService
import net.nemerosa.ontrack.model.docs.Documentation
import net.nemerosa.ontrack.model.docs.DocumentationExampleCode
import net.nemerosa.ontrack.model.events.EventRenderer
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.templating.AbstractTemplatingSource
import net.nemerosa.ontrack.model.templating.TemplatingMisconfiguredConfigParamException
import net.nemerosa.ontrack.model.templating.TemplatingSourceConfig
import org.springframework.stereotype.Component

@Component
@APIDescription("Gets the full SCM commit hash associated with a build, or the assistants which helped write this commit. Renders an empty string if there is none.")
@Documentation(SCMCommitTemplatingSourceConfig::class)
@DocumentationExampleCode("${'$'}{build.scmCommit} or ${'$'}{build.scmCommit?field=assistants}")
class SCMCommitTemplatingSource(
    private val scmDetector: SCMDetector,
    private val scmCommitAssistantService: SCMCommitAssistantService,
) : AbstractTemplatingSource(
    field = "scmCommit",
    type = ProjectEntityType.BUILD,
) {

    override fun render(entity: ProjectEntity, config: TemplatingSourceConfig, renderer: EventRenderer): String {
        val field = config.getString(SCMCommitTemplatingSourceConfig::field.name)?.trim()?.lowercase()
            ?: FIELD_ID
        if (field != FIELD_ID && field != FIELD_ASSISTANTS) {
            throw TemplatingMisconfiguredConfigParamException(
                SCMCommitTemplatingSourceConfig::field.name,
                "Expected `$FIELD_ID` or `$FIELD_ASSISTANTS`."
            )
        }
        if (entity !is Build) return ""
        val scm = scmDetector.getSCM(entity.project) as? SCMChangeLogEnabled ?: return ""
        val commitId = scm.getBuildCommit(entity) ?: return ""
        return if (field == FIELD_ASSISTANTS) {
            val commit = scm.getCommit(commitId) ?: return ""
            scmCommitAssistantService.getAssistants(commit).joinToString(", ") { it.name }
        } else {
            commitId
        }
    }

    companion object {
        /**
         * Full hash of the commit
         */
        const val FIELD_ID = "id"

        /**
         * Names of the assistants of the commit
         */
        const val FIELD_ASSISTANTS = "assistants"
    }

}
