package net.nemerosa.ontrack.extension.scm.changelog

import net.nemerosa.ontrack.extension.scm.changelog.assistants.SCMCommitAssistant
import net.nemerosa.ontrack.extension.scm.changelog.assistants.SCMCommitAssistantService
import net.nemerosa.ontrack.model.events.EventRenderer
import net.nemerosa.ontrack.model.events.renderWithSpace
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.EntityDisplayNameService
import net.nemerosa.ontrack.model.structure.StructureService
import net.nemerosa.ontrack.model.structure.render
import org.springframework.stereotype.Service

@Service
class ChangeLogTemplatingServiceImpl(
    scmChangeLogService: SCMChangeLogService,
    private val entityDisplayNameService: EntityDisplayNameService,
    structureService: StructureService,
    private val scmCommitAssistantService: SCMCommitAssistantService,
) : AbstractChangeLogTemplatingService<ChangeLogTemplatingServiceConfig>(scmChangeLogService, structureService),
    ChangeLogTemplatingService {

    override fun render(
        fromBuild: Build,
        toBuild: Build,
        config: ChangeLogTemplatingServiceConfig,
        renderer: EventRenderer
    ): String {
        return render(
            fromBuild = fromBuild,
            toBuild = toBuild,
            allQualifiers = config.allQualifiers,
            dependencies = config.dependencies,
            defaultQualifierFallback = config.defaultQualifierFallback,
            config = config,
            renderer = renderer,
        )
    }

    override fun renderChangeLog(
        changeLog: SCMChangeLog,
        config: ChangeLogTemplatingServiceConfig,
        suffix: String?,
        renderer: EventRenderer
    ): String {
        // Rendered change log
        val renderedChangeLog = changeLog
            .takeIf { it.from.id() != it.to.id() }
            ?.let { scmChangeLog ->
                renderChangeLogItems(
                    changeLog = scmChangeLog,
                    config = config,
                    renderer = renderer
                )
            } ?: config.empty
        // Title?
        return if (config.title) {
            run {

                val projectName = entityDisplayNameService.render(changeLog.from.project, renderer)
                val fromName = entityDisplayNameService.render(changeLog.from, renderer)
                val toName = entityDisplayNameService.render(changeLog.to, renderer)

                val actualSuffix = suffix ?: ""

                val titleText = if (changeLog.from.id() != changeLog.to.id()) {
                    """
                        Change log for $projectName$actualSuffix from $fromName to $toName
                    """.trimIndent()

                } else {
                    """
                        Project $projectName$actualSuffix version $fromName
                    """.trimIndent()
                }

                renderer.renderSection(
                    title = titleText,
                    content = renderedChangeLog,
                )
            }
        } else {
            renderedChangeLog
        }
    }

    private fun renderChangeLogItems(
        changeLog: SCMChangeLog,
        config: ChangeLogTemplatingServiceConfig,
        renderer: EventRenderer,
    ): String {
        // Assistants of each commit, in the order of the commits, computed only when asked for, so
        // that the default rendering costs nothing more than before
        val assistants: List<List<SCMCommitAssistant>> by lazy {
            changeLog.commits.map { commit -> scmCommitAssistantService.getAssistants(commit.commit) }
        }
        // Issues
        val hasIssues = !changeLog.issues?.issues.isNullOrEmpty()
        val issues = renderChangeLogIssues(renderer, changeLog)
        // Commits
        val commits: String by lazy {
            renderChangeLogCommits(
                changeLog = changeLog,
                commitsMaxLength = config.commitsMaxLength,
                assistants = if (config.assistants) assistants else null,
                renderer = renderer,
            )
        }
        // Everything together
        val items = when (config.commitsOption) {
            ChangeLogTemplatingCommitsOption.NONE -> issues
            ChangeLogTemplatingCommitsOption.OPTIONAL -> if (hasIssues) {
                issues
            } else {
                commits
            }

            ChangeLogTemplatingCommitsOption.ALWAYS ->
                renderer.renderWithSpace(issues, "Commits:", commits)
        }
        // Count of the assisted commits
        val total = changeLog.commits.size
        return if (config.assistedCount && total > 0) {
            val assisted = assistants.count { it.isNotEmpty() }
            val count = renderAssistedCount(assisted, total)
            if (items.isBlank()) {
                count
            } else {
                renderer.renderWithSpace(count, items)
            }
        } else {
            items
        }
    }

    private fun renderAssistedCount(assisted: Int, total: Int): String =
        "$assisted of $total ${if (total == 1) "commit" else "commits"} assisted"

    private fun renderChangeLogCommits(
        changeLog: SCMChangeLog,
        commitsMaxLength: Int,
        assistants: List<List<SCMCommitAssistant>>?,
        renderer: EventRenderer
    ): String = renderer.renderList(
        changeLog.commits.mapIndexed { index, commit ->
            renderChangeLogCommit(
                commit = commit.commit,
                commitsMaxLength = commitsMaxLength,
                assistants = assistants?.get(index) ?: emptyList(),
                renderer = renderer,
            )
        }
    )

    private fun renderChangeLogCommit(
        commit: SCMCommit,
        commitsMaxLength: Int,
        assistants: List<SCMCommitAssistant>,
        renderer: EventRenderer,
    ): String {
        val link = renderer.renderLink(
            text = commit.shortId,
            href = commit.link,
        )
        val line = "$link ${shortCommitMessage(commit.message, commitsMaxLength)}"
        return if (assistants.isEmpty()) {
            line
        } else {
            val names = assistants.joinToString(", ") { assistant ->
                assistant.sessionLink
                    ?.takeIf { it.isNotBlank() }
                    ?.let { renderer.renderLink(text = assistant.name, href = it) }
                    ?: assistant.name
            }
            "$line (assisted by $names)"
        }
    }

}
