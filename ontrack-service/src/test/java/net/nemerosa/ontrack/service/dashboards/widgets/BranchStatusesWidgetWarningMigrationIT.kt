package net.nemerosa.ontrack.service.dashboards.widgets

import tools.jackson.databind.node.ObjectNode
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.dashboards.Dashboard
import net.nemerosa.ontrack.model.dashboards.DashboardContextUserScope
import net.nemerosa.ontrack.model.dashboards.WidgetInstance
import net.nemerosa.ontrack.model.dashboards.WidgetLayout
import net.nemerosa.ontrack.model.dashboards.widgets.BranchStatusesWidget
import net.nemerosa.ontrack.model.dashboards.widgets.BranchStatusesWidgetConfig
import net.nemerosa.ontrack.service.dashboards.DashboardStorageService
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.*
import kotlin.test.assertEquals

class BranchStatusesWidgetWarningMigrationIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var migration: BranchStatusesWidgetWarningMigration

    @Autowired
    private lateinit var dashboardStorageService: DashboardStorageService

    @Autowired
    private lateinit var branchStatusesWidget: BranchStatusesWidget

    @Test
    @AsAdminTest
    fun `Migration of branch statuses widgets for the support of warnings periods`() {
        // Creating an old record
        val record = mapOf(
            "title" to "Sample config",
            "promotions" to listOf("BRONZE", "GOLD"),
            "validations" to listOf("build", "scan"),
            "branches" to listOf(
                mapOf(
                    "project" to "yontrack",
                    "branch" to "main"
                )
            )
        ).asJson()
        val dashboardDef = Dashboard(
            uuid = UUID.randomUUID().toString(),
            name = uid("dash-"),
            userScope = DashboardContextUserScope.PRIVATE,
            widgets = listOf(
                WidgetInstance(
                    uuid = UUID.randomUUID().toString(),
                    key = branchStatusesWidget.key,
                    config = record as ObjectNode,
                    layout = WidgetLayout(x = 0, y = 0, w = 12, h = 12),
                )
            )
        )
        val dashboard = dashboardStorageService.saveDashboard(dashboardDef)

        // Performing the migration
        migration.migration()

        // Reloading the dashboard
        val migratedDashboard = dashboardStorageService.findDashboardByUuid(dashboard.uuid)!!
        // Checks the config has been migrated
        val config = migratedDashboard.widgets.first().config.parse<BranchStatusesWidgetConfig>()
        assertEquals(
            listOf(
                BranchStatusesWidgetConfig.PromotionConfig("BRONZE", null),
                BranchStatusesWidgetConfig.PromotionConfig("GOLD", null)
            ),
            config.promotionConfigs
        )
        assertEquals(
            listOf(
                BranchStatusesWidgetConfig.ValidationConfig("build", null),
                BranchStatusesWidgetConfig.ValidationConfig("scan", null)
            ),
            config.validationConfigs
        )
    }

}