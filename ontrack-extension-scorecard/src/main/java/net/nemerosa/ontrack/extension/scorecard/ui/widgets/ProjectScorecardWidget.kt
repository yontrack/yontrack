package net.nemerosa.ontrack.extension.scorecard.ui.widgets

import net.nemerosa.ontrack.model.dashboards.widgets.AbstractWidget
import net.nemerosa.ontrack.model.dashboards.widgets.WidgetConfig
import org.springframework.stereotype.Component

/**
 * The scorecard of a project on a dashboard: the targets it meets in each of its estates, and its
 * readings.
 *
 * Its data is `Project.scorecard`, as for the Scorecard section of the project page, and so are its
 * project permissions and the licence of the delivery scorecard.
 *
 * The preferred height fits the tabs, the ring and the list of the judged readings at a width of 4
 * of the 12 columns of the grid.
 */
@Component
class ProjectScorecardWidget : AbstractWidget<ProjectScorecardWidgetConfig>(
    key = "extension/scorecard/ProjectScorecard",
    name = "Project scorecard",
    description = "Targets met by a project in each of its estates, and its readings",
    defaultConfig = ProjectScorecardWidgetConfig(project = null),
    preferredHeight = 24,
)

/**
 * @property project Name of the project, required
 * @property set Set shown when the widget opens: `project`, or the name of an estate; the first
 * estate of the project by name when `null`, or the Project set for a project in no estate
 */
data class ProjectScorecardWidgetConfig(
    val project: String?,
    val set: String? = null,
) : WidgetConfig
