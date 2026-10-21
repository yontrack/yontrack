package net.nemerosa.ontrack.service.dashboards

import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.dashboards.*
import net.nemerosa.ontrack.model.security.Roles
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNull

@AsAdminTest
class DashboardServiceIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var dashboardService: DashboardService

    @Autowired
    private lateinit var dashboardStorageService: DashboardStorageService

    @Test
    fun `Default dashboard`() {
        withNoDashboard {
            asUser {
                val dashboards = dashboardService.userDashboards()
                assertEquals(1, dashboards.size)
                val dashboard = dashboards.first()
                assertEquals("0", dashboard.uuid)
                assertEquals("Default dashboard", dashboard.name)
                assertEquals(DashboardContextUserScope.BUILT_IN, dashboard.userScope)
                assertEquals(
                    listOf(
                        WidgetInstance(
                            uuid = "0",
                            key = "home/LastActiveProjects",
                            config = mapOf("count" to 10).asJson(),
                            layout = WidgetLayout(x = 0, y = 0, w = 12, h = 20),
                        )
                    ),
                    dashboard.widgets
                )
            }
        }
    }

    @Test
    fun `Default dashboard is the default dashboard`() {
        withNoDashboard {
            asUser {
                val dashboard = dashboardService.userDashboard()
                assertEquals("0", dashboard.uuid)
                assertEquals("Default dashboard", dashboard.name)
                assertEquals(DashboardContextUserScope.BUILT_IN, dashboard.userScope)
                assertEquals(
                    listOf(
                        WidgetInstance(
                            uuid = "0",
                            key = "home/LastActiveProjects",
                            config = mapOf("count" to 10).asJson(),
                            layout = WidgetLayout(x = 0, y = 0, w = 12, h = 20),
                        )
                    ),
                    dashboard.widgets
                )
            }
        }
    }

    @Test
    fun `Creating a personal dashboard makes it the default dashboard`() {
        val name = uid("dash_")
        withNoDashboard {
            asUser().with(DashboardEdition::class.java).call {
                val dashboard = dashboardService.saveDashboard(
                    SaveDashboardInput(
                        uuid = null,
                        name = name,
                        userScope = DashboardContextUserScope.PRIVATE,
                        widgets = listOf(
                            WidgetInstanceInput(
                                uuid = null,
                                key = "home/LastActiveProjects",
                                config = mapOf("count" to 10).asJson(),
                                layout = WidgetLayout(x = 0, y = 0, w = 12, h = 1),
                            )
                        ),
                        select = true,
                    )
                )
                val selectedDashboard = dashboardService.userDashboard()
                assertEquals(
                    dashboard,
                    selectedDashboard
                )
            }
        }
    }

    @Test
    fun `By default, any user can create and delete a private dashboard`() {
        val name = uid("dash_")
        asUser {
            val dashboard = dashboardService.saveDashboard(
                SaveDashboardInput(
                    uuid = null,
                    name = name,
                    userScope = DashboardContextUserScope.PRIVATE,
                    widgets = listOf(
                        WidgetInstanceInput(
                            uuid = null,
                            key = "home/LastActiveProjects",
                            config = mapOf("count" to 10).asJson(),
                            layout = WidgetLayout(x = 0, y = 0, w = 12, h = 1),
                        )
                    ),
                    select = true,
                )
            )
            val selectedDashboard = dashboardService.userDashboard()
            assertEquals(
                dashboard,
                selectedDashboard
            )
            // Deleting this dashboard
            dashboardService.deleteDashboard(dashboard.uuid)
            assertEquals(
                DashboardContextUserScope.BUILT_IN,
                dashboardService.userDashboard().userScope
            )
        }
    }

    @Test
    fun `By default, any user can delete a shared dashboard if owned by them`() {
        val name = uid("dash_")
        asUser {
            val dashboard = dashboardService.saveDashboard(
                SaveDashboardInput(
                    uuid = null,
                    name = name,
                    userScope = DashboardContextUserScope.SHARED,
                    widgets = listOf(
                        WidgetInstanceInput(
                            uuid = null,
                            key = "home/LastActiveProjects",
                            config = mapOf("count" to 10).asJson(),
                            layout = WidgetLayout(x = 0, y = 0, w = 12, h = 1),
                        )
                    ),
                    select = true,
                )
            )
            // Deleting this dashboard
            dashboardService.deleteDashboard(dashboard.uuid)
            // Dashboard has been deleted
            assertNull(
                dashboardStorageService.findDashboardByUuid(dashboard.uuid),
                "Dashboard has been deleted"
            )
        }
    }

    @Test
    fun `Administrators can delete any dashboard`() {
        val name = uid("dash_")
        asUser {
            val dashboard = dashboardService.saveDashboard(
                SaveDashboardInput(
                    uuid = null,
                    name = name,
                    userScope = DashboardContextUserScope.SHARED,
                    widgets = listOf(
                        WidgetInstanceInput(
                            uuid = null,
                            key = "home/LastActiveProjects",
                            config = mapOf("count" to 10).asJson(),
                            layout = WidgetLayout(x = 0, y = 0, w = 12, h = 1),
                        )
                    ),
                    select = true,
                )
            )
            // Deleting this dashboard with an administrator account
            asAccountWithGlobalRole(Roles.GLOBAL_ADMINISTRATOR) {
                dashboardService.deleteDashboard(dashboard.uuid)
            }
            // Dashboard has been deleted
            assertNull(
                dashboardStorageService.findDashboardByUuid(dashboard.uuid),
                "Dashboard has been deleted"
            )
        }
    }

    @Test
    fun `Deleting a shared dashboard makes it unavailable as the default dashboard for all users`() {
        val participant = doCreateAccountWithGlobalRole(Roles.GLOBAL_PARTICIPANT)
        val admin = doCreateAccountWithGlobalRole(Roles.GLOBAL_ADMINISTRATOR)

        // Admin creating & sharing a dashboard
        val dashboard = asFixedAccount(admin) {
            dashboardService.saveDashboard(
                SaveDashboardInput(
                    uuid = null,
                    name = uid("dsh_"),
                    userScope = DashboardContextUserScope.SHARED,
                    widgets = emptyList(),
                    select = false,
                )
            )
        }

        // User to make this dashboard their default
        asFixedAccount(participant) {
            dashboardService.selectDashboard(dashboard.uuid)
        }

        // Checking the default dashboard for the user
        asFixedAccount(participant) {
            assertEquals(
                dashboard.uuid,
                dashboardService.userDashboard().uuid,
                "Dashboard is selected"
            )
        }

        // Deleting the dashboard
        asFixedAccount(admin) {
            dashboardService.deleteDashboard(dashboard.uuid)
        }

        // Checking we're back on the default dashboard
        asFixedAccount(participant) {
            assertEquals(
                DefaultDashboards.defaultDashboard.uuid,
                dashboardService.userDashboard().uuid,
                "Default dashboard is selected"
            )
        }
    }

    @Test
    fun `A branch statuses widget whose config still carries the removed refresh interval saves and reads back`() {
        // The refresh interval was removed from the widget in 6.0 (#2104): a stored dashboard or an
        // imported YAML file may still carry it, and it must be ignored.
        val config = mapOf(
            "title" to "Branch statuses",
            "branches" to listOf(
                mapOf("project" to "P", "branch" to "main"),
            ),
            "refreshInterval" to mapOf("count" to 5, "unit" to "M"),
        ).asJson()
        asUser {
            val dashboard = dashboardService.saveDashboard(
                SaveDashboardInput(
                    uuid = null,
                    name = uid("dash_"),
                    userScope = DashboardContextUserScope.PRIVATE,
                    widgets = listOf(
                        WidgetInstanceInput(
                            uuid = null,
                            key = "home/BranchStatuses",
                            config = config,
                            layout = WidgetLayout(x = 0, y = 0, w = 12, h = 30),
                        )
                    ),
                    select = false,
                )
            )
            val read = dashboardService.findDashboardByUuid(dashboard.uuid)
            assertEquals(dashboard, read)
            assertEquals(config, read?.widgets?.single()?.config)
        }
    }

    private fun withNoDashboard(code: () -> Unit) {
        asAdmin {
            val dashboards = dashboardService.userDashboards()
            dashboards.forEach {
                if (it.userScope != DashboardContextUserScope.BUILT_IN) {
                    dashboardService.deleteDashboard(it.uuid)
                }
            }
            code()
        }
    }

}