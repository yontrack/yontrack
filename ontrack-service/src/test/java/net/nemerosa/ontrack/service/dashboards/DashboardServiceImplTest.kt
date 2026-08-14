package net.nemerosa.ontrack.service.dashboards

import io.mockk.mockk
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.dashboards.Dashboard
import net.nemerosa.ontrack.model.dashboards.DashboardContextUserScope
import net.nemerosa.ontrack.model.dashboards.WidgetInstance
import net.nemerosa.ontrack.model.dashboards.WidgetLayout
import com.fasterxml.jackson.databind.node.NullNode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for the YAML export format of a dashboard.
 *
 * The export must be _ready to use_: a collection of one dashboard, without any UUID,
 * directly acceptable by `applyDashboards`.
 */
class DashboardServiceImplTest {

    private val service = DashboardServiceImpl(
        securityService = mockk(),
        preferencesService = mockk(),
        dashboardStorageService = mockk(),
    )

    @Test
    fun `dashboard as yaml`() {
        val dashboard = Dashboard(
            uuid = "dashboard-uuid",
            name = "My dashboard",
            userScope = DashboardContextUserScope.SHARED,
            widgets = listOf(
                WidgetInstance(
                    uuid = "widget-uuid-1",
                    key = "home/LastActiveProjects",
                    config = """{"count":10}""".parseAsJson(),
                    layout = WidgetLayout(x = 0, y = 0, w = 6, h = 25),
                ),
                WidgetInstance(
                    uuid = "widget-uuid-2",
                    key = "home/FavouriteProjects",
                    config = NullNode.instance,
                    layout = WidgetLayout(x = 6, y = 0, w = 6, h = 25),
                ),
            ),
        )
        // Note: the "y" key is quoted because it is a boolean in YAML 1.1
        assertEquals(
            """
                - name: "My dashboard"
                  widgets:
                  - key: "home/LastActiveProjects"
                    layout:
                      x: 0
                      "y": 0
                      w: 6
                      h: 25
                    config:
                      count: 10
                  - key: "home/FavouriteProjects"
                    layout:
                      x: 6
                      "y": 0
                      w: 6
                      h: 25
            """.trimIndent(),
            service.dashboardAsYaml(dashboard).trim()
        )
    }

    @Test
    fun `dashboard without any widget as yaml`() {
        val dashboard = Dashboard(
            uuid = "dashboard-uuid",
            name = "My dashboard",
            userScope = DashboardContextUserScope.SHARED,
            widgets = emptyList(),
        )
        assertEquals(
            """
                - name: "My dashboard"
                  widgets: []
            """.trimIndent(),
            service.dashboardAsYaml(dashboard).trim()
        )
    }

}
