package net.nemerosa.ontrack.model.dashboards.widgets

import net.nemerosa.ontrack.common.api.APIDescription
import org.springframework.stereotype.Component

@Component
class LastBuildsForBranchWidget : AbstractWidget<LastBuildsForBranchWidgetConfig>(
    key = "home/LastBuildsForBranch",
    name = "Last builds for a branch",
    description = "Displays the last builds of a branch, with their promotions and validations",
    defaultConfig = LastBuildsForBranchWidgetConfig(),
    preferredHeight = 30,
)

data class LastBuildsForBranchWidgetConfig(
    @APIDescription("Overriding the title of the widget")
    val title: String? = null,
    @APIDescription("Name of the project")
    val project: String? = null,
    @APIDescription("Name of the branch")
    val branch: String? = null,
    @APIDescription("Number of builds to display")
    val count: Int = 10,
    @APIDescription("Promotions to display for each build. All of them if empty.")
    val promotions: List<String> = emptyList(),
    @APIDescription("Validations to display as columns. All of them if empty.")
    val validations: List<String> = emptyList(),
) : WidgetConfig
