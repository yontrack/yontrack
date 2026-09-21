package net.nemerosa.ontrack.service.dashboards

import tools.jackson.databind.node.NullNode
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.model.dashboards.Dashboard
import net.nemerosa.ontrack.model.dashboards.DashboardContextUserScope
import net.nemerosa.ontrack.model.dashboards.WidgetInstance
import net.nemerosa.ontrack.model.dashboards.WidgetLayout
import net.nemerosa.ontrack.model.support.StorageService
import net.nemerosa.ontrack.test.TestUtils
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DashboardWidgetHeight5MigrationIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var dashboardWidgetHeight5Migration: DashboardWidgetHeight5Migration

    @Autowired
    private lateinit var dashboardStorageService: DashboardStorageService

    @Autowired
    private lateinit var storageService: StorageService

    @Test
    fun `Migration of dashboard widget heights x 5`() {
        // Creating a dashboard
        val dashboard = Dashboard(
            uuid = UUID.randomUUID().toString(),
            name = TestUtils.uid("dashboard-"),
            userScope = DashboardContextUserScope.PRIVATE,
            widgets = listOf(
                WidgetInstance(
                    uuid = UUID.randomUUID().toString(),
                    key = "widget-1",
                    config = NullNode.instance,
                    layout = WidgetLayout(x = 0, y = 0, w = 12, h = 6),
                ),
                WidgetInstance(
                    uuid = UUID.randomUUID().toString(),
                    key = "widget-2",
                    config = NullNode.instance,
                    layout = WidgetLayout(x = 0, y = 6, w = 12, h = 9),
                ),
            )
        )
        dashboardStorageService.saveDashboard(dashboard)

        // Making sure the migration is marked as not being done yet
        storageService.delete(DashboardWidgetHeight5Migration::class.java.name, "migration")

        // Running the migration & checking the new heights
        val migration = {
            dashboardWidgetHeight5Migration.start()
            assertNotNull(dashboardStorageService.findDashboardByUuid(dashboard.uuid)) {
                val layouts = it.widgets.map { w -> w.layout }
                assertEquals(
                    listOf(
                        WidgetLayout(x = 0, y = 0, w = 12, h = 30),
                        WidgetLayout(x = 0, y = 30, w = 12, h = 45),
                    ),
                    layouts,
                )
            }
        }

        // Running the migration twice to check idempotency
        repeat(2) { migration() }
    }

}