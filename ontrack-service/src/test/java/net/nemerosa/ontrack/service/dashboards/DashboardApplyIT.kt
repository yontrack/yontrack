package net.nemerosa.ontrack.service.dashboards

import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.json.getRequiredTextField
import net.nemerosa.ontrack.model.dashboards.DashboardContextUserScope
import net.nemerosa.ontrack.model.dashboards.DashboardService
import net.nemerosa.ontrack.model.security.Roles
import net.nemerosa.ontrack.test.TestUtils.uid
import net.nemerosa.ontrack.yaml.Yaml
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DashboardApplyIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var dashboardService: DashboardService

    @Test
    fun `applyDashboards creates a shared dashboard`() {
        val name = uid("dash_")
        asAccountWithGlobalRole(Roles.GLOBAL_ADMINISTRATOR) {
            dashboardService.applyDashboards(
                """
                - name: "$name"
                  widgets:
                    - key: "home/LastActiveProjects"
                      layout: {x: 0, y: 0, w: 6, h: 25}
                      config: {count: 10}
                """.trimIndent()
            )
            val shared = dashboardService.userDashboards()
                .filter { it.userScope == DashboardContextUserScope.SHARED }
            val dashboard = shared.find { it.name == name }
            assertNotNull(dashboard, "Dashboard '$name' should be created as SHARED")
            assertEquals(1, dashboard.widgets.size)
            assertEquals("home/LastActiveProjects", dashboard.widgets[0].key)
            assertEquals(10, dashboard.widgets[0].config.path("count").asInt())
        }
    }

    @Test
    fun `applyDashboards is idempotent on re-application`() {
        val name = uid("dash_")
        val yaml = """
            - name: "$name"
              widgets: []
        """.trimIndent()
        asAccountWithGlobalRole(Roles.GLOBAL_ADMINISTRATOR) {
            dashboardService.applyDashboards(yaml)
            val firstUuid = dashboardService.userDashboards()
                .first { it.name == name }.uuid
            dashboardService.applyDashboards(yaml)
            val all = dashboardService.userDashboards().filter { it.name == name }
            assertEquals(1, all.size, "No duplicate should be created")
            assertEquals(firstUuid, all[0].uuid, "UUID must be stable")
        }
    }

    @Test
    fun `applyDashboards updates existing dashboard by name`() {
        val name = uid("dash_")
        asAccountWithGlobalRole(Roles.GLOBAL_ADMINISTRATOR) {
            dashboardService.applyDashboards(
                """
                - name: "$name"
                  widgets:
                    - key: "home/LastActiveProjects"
                      layout: {x: 0, y: 0, w: 6, h: 25}
                      config: {count: 5}
                """.trimIndent()
            )
            val firstUuid = dashboardService.userDashboards().first { it.name == name }.uuid

            dashboardService.applyDashboards(
                """
                - name: "$name"
                  widgets:
                    - key: "home/LastActiveProjects"
                      layout: {x: 0, y: 0, w: 12, h: 25}
                      config: {count: 20}
                """.trimIndent()
            )
            val updated = dashboardService.userDashboards().first { it.name == name }
            assertEquals(firstUuid, updated.uuid, "UUID must be stable across updates")
            assertEquals(12, updated.widgets[0].layout.w, "Layout should be updated")
            assertEquals(20, updated.widgets[0].config.path("count").asInt(), "Config should be updated")
        }
    }

    @Test
    fun `applyDashboards preserves dashboards not in the YAML`() {
        val cascName = uid("casc_")
        val uiName = uid("ui_")
        asAccountWithGlobalRole(Roles.GLOBAL_ADMINISTRATOR) {
            dashboardService.applyDashboards(
                """
                - name: "$uiName"
                  widgets: []
                """.trimIndent()
            )
            dashboardService.applyDashboards(
                """
                - name: "$cascName"
                  widgets: []
                """.trimIndent()
            )
            val names = dashboardService.userDashboards().map { it.name }
            assertTrue(cascName in names, "CasC dashboard must exist")
            assertTrue(uiName in names, "Pre-existing dashboard must be preserved")
        }
    }

    @Test
    fun `UUID is deterministic from name when not provided`() {
        val name = uid("det_")
        val yaml = """
            - name: "$name"
              widgets: []
        """.trimIndent()
        asAccountWithGlobalRole(Roles.GLOBAL_ADMINISTRATOR) {
            dashboardService.applyDashboards(yaml)
            val uuid1 = dashboardService.userDashboards().first { it.name == name }.uuid
            dashboardService.deleteDashboard(uuid1)
            dashboardService.applyDashboards(yaml)
            val uuid2 = dashboardService.userDashboards().first { it.name == name }.uuid
            assertEquals(uuid1, uuid2, "UUID must be deterministically derived from name")
        }
    }

    @Test
    fun `dashboardAsYaml round-trips through applyDashboards`() {
        val name = uid("rt_")
        asAccountWithGlobalRole(Roles.GLOBAL_ADMINISTRATOR) {
            dashboardService.applyDashboards(
                """
                - name: "$name"
                  widgets:
                    - key: "home/LastActiveProjects"
                      layout: {x: 0, y: 0, w: 6, h: 25}
                      config: {count: 10}
                """.trimIndent()
            )
            val dashboard = dashboardService.userDashboards().first { it.name == name }
            val yaml = dashboardService.dashboardAsYaml(dashboard)

            // Delete and re-apply from exported YAML
            dashboardService.deleteDashboard(dashboard.uuid)
            dashboardService.applyDashboards(yaml)

            val restored = dashboardService.userDashboards().filter { it.name == name }
            assertEquals(1, restored.size, "Exactly one dashboard after round-trip")
            assertEquals(dashboard.uuid, restored[0].uuid, "UUID deterministically derived from the name")
            assertEquals(10, restored[0].widgets[0].config.path("count").asInt())
        }
    }

    @Test
    fun `dashboardAsYaml exports a ready-to-use collection of one dashboard`() {
        val name = uid("exp_")
        asAccountWithGlobalRole(Roles.GLOBAL_ADMINISTRATOR) {
            dashboardService.applyDashboards(
                """
                - name: "$name"
                  widgets:
                    - key: "home/LastActiveProjects"
                      layout: {x: 0, y: 0, w: 6, h: 25}
                      config: {count: 10}
                """.trimIndent()
            )
            val dashboard = dashboardService.userDashboards().first { it.name == name }
            val yaml = dashboardService.dashboardAsYaml(dashboard)

            assertTrue(yaml.startsWith("- name:"), "Export is a collection, with no document start marker:\n$yaml")
            assertFalse("uuid" in yaml, "Export contains no UUID:\n$yaml")

            // The export must parse back as a list of one definition
            val documents = Yaml().read(yaml)
            assertEquals(1, documents.size, "One single YAML document")
            val list = documents.first()
            assertTrue(list.isArray, "Root of the document is a list")
            assertEquals(1, list.size(), "List of one dashboard")
            assertEquals(name, list.first().getRequiredTextField("name"))
        }
    }

    @Test
    fun `dashboardAsYaml re-applies onto the same dashboard when its UUID is not derived from its name`() {
        val name = uid("nid_")
        val uuid = UUID.randomUUID().toString()
        asAccountWithGlobalRole(Roles.GLOBAL_ADMINISTRATOR) {
            dashboardService.applyDashboards(
                """
                - uuid: "$uuid"
                  name: "$name"
                  widgets:
                    - key: "home/LastActiveProjects"
                      layout: {x: 0, y: 0, w: 6, h: 25}
                      config: {count: 10}
                """.trimIndent()
            )
            val dashboard = dashboardService.userDashboards().first { it.name == name }
            assertEquals(uuid, dashboard.uuid, "Explicit UUID used at creation time")

            // Re-applying the export (which carries no UUID) must match the existing dashboard by name
            dashboardService.applyDashboards(dashboardService.dashboardAsYaml(dashboard))

            val restored = dashboardService.userDashboards().filter { it.name == name }
            assertEquals(1, restored.size, "No duplicate created by the export")
            assertEquals(uuid, restored[0].uuid, "Existing dashboard updated in place")
        }
    }

    @Test
    fun `dashboardAsYaml omits the config of a widget without configuration`() {
        val name = uid("nocfg_")
        asAccountWithGlobalRole(Roles.GLOBAL_ADMINISTRATOR) {
            dashboardService.applyDashboards(
                """
                - name: "$name"
                  widgets:
                    - key: "home/LastActiveProjects"
                      layout: {x: 0, y: 0, w: 6, h: 25}
                """.trimIndent()
            )
            val dashboard = dashboardService.userDashboards().first { it.name == name }
            val yaml = dashboardService.dashboardAsYaml(dashboard)
            assertFalse("config" in yaml, "No null config in the export:\n$yaml")
            // Still usable
            dashboardService.applyDashboards(yaml)
            assertEquals(1, dashboardService.userDashboards().count { it.name == name })
        }
    }

}
