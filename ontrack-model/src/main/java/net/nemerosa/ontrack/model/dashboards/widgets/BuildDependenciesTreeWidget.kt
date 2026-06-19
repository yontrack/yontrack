package net.nemerosa.ontrack.model.dashboards.widgets

import org.springframework.stereotype.Component

@Component
class BuildDependenciesTreeWidget : AbstractWidget<BuildDependenciesTreeWidgetConfig>(
    key = "home/BuildDependenciesTree",
    name = "Build dependencies tree",
    description = "Displays the downstream dependency tree for the latest build promoted to a given promotion level.",
    defaultConfig = BuildDependenciesTreeWidgetConfig(),
    preferredHeight = 30,
)

data class BuildDependenciesTreeWidgetConfig(
    val title: String = "Build dependencies",
    val project: String = "",
    val branch: String = "",
    val promotionLevel: String = "",
) : WidgetConfig
