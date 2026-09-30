package net.nemerosa.ontrack.extension.findings.ui.widgets

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.model.dashboards.widgets.AbstractWidget
import net.nemerosa.ontrack.model.dashboards.widgets.WidgetConfig
import org.springframework.stereotype.Component

/**
 * Findings open on a branch by severity, with the ones accepted and resolved on it.
 */
@Component
class BranchFindingsWidget : AbstractWidget<BranchFindingsWidgetConfig>(
    key = "extension/findings/BranchFindings",
    name = "Branch findings",
    description = "Security findings open on a branch by severity, with the ones accepted and resolved on it",
    defaultConfig = BranchFindingsWidgetConfig(),
    preferredHeight = 12,
)

data class BranchFindingsWidgetConfig(
    @APIDescription("Name of the project")
    val project: String? = null,
    @APIDescription("Name of the branch")
    val branch: String? = null,
) : WidgetConfig
