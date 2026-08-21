package net.nemerosa.ontrack.extension.scm.changelog

import net.nemerosa.ontrack.extension.issues.model.Issue
import net.nemerosa.ontrack.extension.issues.model.IssueServiceConfiguration
import net.nemerosa.ontrack.extension.scm.service.SCMDetector
import net.nemerosa.ontrack.model.events.EventRenderer
import net.nemerosa.ontrack.model.events.EventRendererRegistry
import net.nemerosa.ontrack.model.events.PlainEventRenderer
import org.apache.commons.text.StringEscapeUtils
import org.springframework.stereotype.Service

@Service
class SCMChangeLogExportServiceImpl(
    private val eventRendererRegistry: EventRendererRegistry,
    private val scmDetector: SCMDetector,
) : SCMChangeLogExportService {

    override fun export(
        changeLog: SCMChangeLog?,
        input: SCMChangeLogExportInput?,
    ): String {
        // No change log, no export
        if (changeLog == null) return ""

        // No issues, no change log
        val changeLogIssues = changeLog.issues
        if (changeLogIssues == null || changeLogIssues.issues.isEmpty()) return ""

        // Default input
        val actualInput = input ?: SCMChangeLogExportInput()

        // Actual format to use
        val format = actualInput.format ?: PlainEventRenderer.INSTANCE.id
        val renderer = eventRendererRegistry.findEventRendererById(format) ?: PlainEventRenderer.INSTANCE

        // Getting the project for the change log
        val project = changeLog.from.project

        // Getting its SCM
        val scm = scmDetector.getSCM(project)
        if (scm == null || scm !is SCMChangeLogEnabled) return ""

        // Issue service configuration
        val configuredIssueService = scm.getConfiguredIssueService()
            ?: return ""

        // Grouping the issues according to the specification
        val groupedIssues = groupIssues(
            issues = changeLogIssues.issues,
            input = actualInput,
        ) { issue: Issue ->
            configuredIssueService.issueServiceExtension.getIssueTypes(
                configuredIssueService.issueServiceConfiguration,
                issue
            )
        }

        // Formatting
        val s = StringBuilder()
        var count = 0
        groupedIssues.forEach { (groupName, issues) ->
            // Previous group?
            if (count > 0) {
                s.append(renderer.renderSpace())
            }
            count++
            // Rendering the list of issues
            val renderedIssues = renderer.renderList(
                issues.map { issue ->
                    renderIssue(issue, renderer)
                }
            )
            // Group section
            if (groupName.isNotBlank()) {
                s.append(renderer.renderSection(groupName, renderedIssues))
            } else {
                s.append(renderedIssues)
            }
        }

        // OK
        return s.toString()
    }

    private fun renderIssue(issue: Issue, renderer: EventRenderer): String {
        return """
            ${renderer.renderLink(issue.displayKey, issue.url)} ${issue.summary}
        """.trimIndent()
    }

}