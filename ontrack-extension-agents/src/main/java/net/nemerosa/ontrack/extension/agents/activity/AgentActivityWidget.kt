package net.nemerosa.ontrack.extension.agents.activity

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.model.dashboards.widgets.AbstractWidget
import net.nemerosa.ontrack.model.dashboards.widgets.WidgetConfig
import org.springframework.stereotype.Component

/**
 * *Agent activity*: how much of the delivery the agents drive over a window of days, across the
 * projects the user can see (#2035). Licensed, under the agent governance.
 */
@Component
class AgentActivityWidget : AbstractWidget<AgentActivityWidgetConfig>(
    key = "extension/agents/AgentActivity",
    name = "Agent activity",
    description = "Builds, promotions and deployments by agents, and the assisted share of the builds, over a window of days (requires the Agent governance licence)",
    defaultConfig = AgentActivityWidgetConfig(),
    preferredHeight = 12,
)

data class AgentActivityWidgetConfig(
    @APIDescription("Number of days of the window, ending now: 7, 30 or 90")
    val window: Int = AgentActivityStatsFilter.DEFAULT_WINDOW,
    @APIDescription("Names of the projects to narrow the counts to - all the visible projects when empty")
    val projects: List<String> = emptyList(),
    @APIDescription("Labels the projects must all carry, as `category:name` - no restriction when empty")
    val labels: List<String> = emptyList(),
) : WidgetConfig
