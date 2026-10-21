package net.nemerosa.ontrack.model.dashboards.widgets

import net.nemerosa.ontrack.common.api.APIDescription
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class BranchStatusesWidget : AbstractWidget<BranchStatusesWidgetConfig>(
    key = "home/BranchStatuses",
    name = "Branch statuses",
    description = "Displays the promotions & validations for a set of branches",
    defaultConfig = BranchStatusesWidgetConfig(),
    preferredHeight = 30,
)

data class BranchStatusesWidgetConfig(
    val title: String = "Branch statuses",
    val promotionConfigs: List<PromotionConfig> = emptyList(),
    val validationConfigs: List<ValidationConfig> = emptyList(),
    val branches: List<BranchStatusesWidgetConfigBranch> = emptyList(),
    @APIDescription("If checked, displays additional results with each validation, like test summary, etc.")
    val displayValidationResults: Boolean = false,
    @APIDescription("If checked, displays run info")
    val displayValidationRun: Boolean = false,
) : WidgetConfig {

    data class BranchStatusesWidgetConfigBranch(
        val project: String,
        val branch: String,
    )

    data class PromotionConfig(
        val promotionLevel: String,
        val period: Duration?,
    )

    data class ValidationConfig(
        val validationStamp: String,
        val period: Duration?,
    )

}
