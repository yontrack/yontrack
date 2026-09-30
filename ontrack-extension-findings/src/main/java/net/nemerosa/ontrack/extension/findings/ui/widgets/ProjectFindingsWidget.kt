package net.nemerosa.ontrack.extension.findings.ui.widgets

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.model.dashboards.widgets.AbstractWidget
import net.nemerosa.ontrack.model.dashboards.widgets.WidgetConfig
import org.springframework.stereotype.Component

/**
 * Open findings of a project by severity, with its accepted and resolved ones, and optionally
 * their exposure on the branches which count for the project.
 */
@Component
class ProjectFindingsWidget : AbstractWidget<ProjectFindingsWidgetConfig>(
    key = "extension/findings/ProjectFindings",
    name = "Project findings",
    description = "Open security findings of a project by severity, and optionally their exposure per branch",
    defaultConfig = ProjectFindingsWidgetConfig(),
    preferredHeight = 20,
)

data class ProjectFindingsWidgetConfig(
    @APIDescription("Name of the project")
    val project: String? = null,
    @APIDescription("If checked, displays the branches having open findings, among the ones which count for the project")
    val showBranches: Boolean = false,
) : WidgetConfig
