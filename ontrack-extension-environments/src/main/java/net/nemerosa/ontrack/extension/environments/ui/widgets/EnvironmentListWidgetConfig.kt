package net.nemerosa.ontrack.extension.environments.ui.widgets

import net.nemerosa.ontrack.model.dashboards.widgets.WidgetConfig

/**
 * @property title Overrides the cell's title
 * @property tags Environment tags the columns are restricted to
 * @property projects Exact project names the rows are restricted to
 * @property rowLimit How many projects the widget shows. A dashboard cell is short, so the widget
 *   draws its first rows and stops rather than paging inside a tile - and the number has to be
 *   configurable because how many rows fit is a property of the cell somebody sized, not of the data.
 */
data class EnvironmentListWidgetConfig(
    val title: String,
    val tags: List<String>,
    val projects: List<String>,
    val rowLimit: Int = DEFAULT_ROW_LIMIT,
) : WidgetConfig {

    companion object {
        /**
         * Ten rows is about what the widget's default height shows.
         */
        const val DEFAULT_ROW_LIMIT = 10

        fun default() = EnvironmentListWidgetConfig(
            title = "",
            tags = emptyList(),
            projects = emptyList(),
            rowLimit = DEFAULT_ROW_LIMIT,
        )
    }

}
