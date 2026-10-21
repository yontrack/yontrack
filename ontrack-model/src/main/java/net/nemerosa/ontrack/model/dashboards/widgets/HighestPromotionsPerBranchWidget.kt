package net.nemerosa.ontrack.model.dashboards.widgets

import net.nemerosa.ontrack.common.api.APIDescription
import org.springframework.stereotype.Component

@Component
class HighestPromotionsPerBranchWidget : AbstractWidget<HighestPromotionsPerBranchWidgetConfig>(
    key = "home/HighestPromotionsPerBranch",
    name = "Highest promotions per branch",
    description = "Displays, for a list of branches, the highest promotion of their latest build",
    defaultConfig = HighestPromotionsPerBranchWidgetConfig(),
    preferredHeight = 30,
)

data class HighestPromotionsPerBranchWidgetConfig(
    @APIDescription("Overrides the default title, Highest promotions per branch")
    val title: String? = null,
    @APIDescription("The format shown until a viewer switches it in the widget")
    val format: HighestPromotionsPerBranchWidgetFormat = HighestPromotionsPerBranchWidgetFormat.TABLE,
    @APIDescription("The branches to show, in this order")
    val branches: List<HighestPromotionsPerBranchWidgetConfigBranch> = emptyList(),
) : WidgetConfig

enum class HighestPromotionsPerBranchWidgetFormat {
    @APIDescription("One line per branch")
    TABLE,

    @APIDescription("One line per branch, with all the promotion levels of the branch")
    LADDER,
}

data class HighestPromotionsPerBranchWidgetConfigBranch(
    @APIDescription("Name of the project")
    val project: String,
    @APIDescription("Exact name of the branch. Exactly one of branch and pattern must be set.")
    val branch: String? = null,
    @APIDescription("Regular expression on the name of the branches. Exactly one of branch and pattern must be set.")
    val pattern: String? = null,
    @APIDescription("Maximum number of branches a pattern brings, the most recently active first. Only meaningful with a pattern.")
    val count: Int = 5,
)
