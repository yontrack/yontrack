package net.nemerosa.ontrack.graphql.dashboards

import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.getRequiredBooleanField
import net.nemerosa.ontrack.json.getRequiredJsonField
import net.nemerosa.ontrack.json.getRequiredTextField
import net.nemerosa.ontrack.model.dashboards.*
import net.nemerosa.ontrack.model.security.Roles
import net.nemerosa.ontrack.test.TestUtils
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class DashboardControllerIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var dashboardService: DashboardService

    @Test
    fun `Default dashboard`() {
        withNoDashboard {
            asUser {
                run(
                    """
                    {
                        userDashboards {
                            uuid
                        }
                    }
                """
                ) { data ->
                    val uuids = data.path("userDashboards").map { it.getRequiredTextField("uuid") }
                    assertEquals(listOf("0"), uuids)
                }
            }
        }
    }

    @Test
    fun `Default dashboard is the default dashboard`() {
        withNoDashboard {
            asUser {
                run(
                    """
                    {
                        userDashboard {
                            uuid
                        }
                    }
                """
                ) { data ->
                    val uuid = data.path("userDashboard").getRequiredTextField("uuid")
                    assertEquals("0", uuid)
                }
            }
        }
    }

    @Test
    fun `Creating a personal dashboard makes it the default dashboard`() {
        val name = TestUtils.uid("dash_")
        withNoDashboard {
            asUser().with(DashboardEdition::class.java).call {
                run(
                    """
                    mutation SaveDashboard {
                        saveDashboard(input: {
                            name: "$name",
                            userScope: PRIVATE,
                            widgets: {
                                key: "home/LastActiveProjects",
                                config: { count: 10 },
                                layout: { x: 0, y: 0, w: 12, h: 1 }
                            },
                            select: true,
                        }) {
                            dashboard {
                                uuid
                            }
                            errors {
                                message
                            }
                        }
                    }
                """
                ) { data ->
                    checkGraphQLUserErrors(data, "saveDashboard")
                    val uuid = data.path("saveDashboard").path("dashboard").getRequiredTextField("uuid")
                    run(
                        """
                        {
                            userDashboard {
                                uuid
                            }
                        }
                    """
                    ) { x ->
                        assertEquals(uuid, x.path("userDashboard").getRequiredTextField("uuid"))
                    }
                }
            }
        }
    }

    @Test
    fun `Getting the authorizations on the dashboards`() {
        val name = TestUtils.uid("dash_")
        withNoDashboard {
            asUser {
                val uuid = dashboardService.saveDashboard(
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
                ).uuid
                // Getting the authorizations
                run(
                    """
                    {
                        userDashboards {
                            uuid
                            authorizations {
                                edit
                                share
                                delete
                            }
                        }
                    }
                """
                ) { data ->
                    val dashboard = data.path("userDashboards")
                        .find { it.getRequiredTextField("uuid") == uuid }
                        ?: fail("Cannot find created dashboard")
                    val authorizations = dashboard.getRequiredJsonField("authorizations")
                    assertEquals(true, authorizations.getRequiredBooleanField("edit"))
                    assertEquals(true, authorizations.getRequiredBooleanField("share"))
                    assertEquals(true, authorizations.getRequiredBooleanField("delete"))
                }
            }
        }
    }

    @Test
    fun `applyDashboards mutation creates a shared dashboard`() {
        val name = uid("dash_")
        val yaml = """
            - name: "$name"
              widgets: []
        """.trimIndent()
        asAccountWithGlobalRole(Roles.GLOBAL_ADMINISTRATOR) {
            run(
                """
                mutation ApplyDashboards(${'$'}yaml: String!) {
                    applyDashboards(input: { yaml: ${'$'}yaml }) {
                        dashboards { uuid name userScope }
                        errors { message }
                    }
                }
                """,
                mapOf("yaml" to yaml)
            ) { data ->
                checkGraphQLUserErrors(data, "applyDashboards")
                val dashboards = data.path("applyDashboards").path("dashboards")
                assertEquals(1, dashboards.size())
                assertEquals(name, dashboards.first().getRequiredTextField("name"))
                assertEquals("SHARED", dashboards.first().getRequiredTextField("userScope"))
            }
        }
    }

    @Test
    fun `applyDashboards mutation is idempotent`() {
        val name = uid("dash_")
        val yaml = """
            - name: "$name"
              widgets: []
        """.trimIndent()
        asAccountWithGlobalRole(Roles.GLOBAL_ADMINISTRATOR) {
            val applyQuery = """
                mutation ApplyDashboards(${'$'}yaml: String!) {
                    applyDashboards(input: { yaml: ${'$'}yaml }) {
                        dashboards { uuid }
                        errors { message }
                    }
                }
            """
            run(applyQuery, mapOf("yaml" to yaml)) { data ->
                checkGraphQLUserErrors(data, "applyDashboards")
            }
            val firstUuid = dashboardService.userDashboards().first { it.name == name }.uuid
            run(applyQuery, mapOf("yaml" to yaml)) { data ->
                checkGraphQLUserErrors(data, "applyDashboards")
            }
            val all = dashboardService.userDashboards().filter { it.name == name }
            assertEquals(1, all.size, "No duplicate after re-applying same YAML")
            assertEquals(firstUuid, all[0].uuid, "UUID must be stable")
        }
    }

    @Test
    fun `asYaml field returns exportable YAML for a dashboard`() {
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
            run("""{ userDashboards { name asYaml } }""") { data ->
                val dashboard = data.path("userDashboards")
                    .find { it.getRequiredTextField("name") == name }
                    ?: fail("Dashboard '$name' not found")
                val yaml = dashboard.getRequiredTextField("asYaml")
                assertTrue(yaml.contains(name), "YAML should contain dashboard name")
                assertTrue(yaml.contains("LastActiveProjects"), "YAML should contain widget key")
                assertTrue(yaml.contains("count"), "YAML should contain widget config field")
            }
        }
    }

    @Test
    fun `applyDashboards and asYaml round-trip via GraphQL`() {
        val name = uid("rt_")
        val initialYaml = """
            - name: "$name"
              widgets:
                - key: "home/LastActiveProjects"
                  layout: {x: 0, y: 0, w: 6, h: 25}
                  config: {count: 7}
        """.trimIndent()
        val applyQuery = """
            mutation ApplyDashboards(${'$'}yaml: String!) {
                applyDashboards(input: { yaml: ${'$'}yaml }) {
                    dashboards { uuid name }
                    errors { message }
                }
            }
        """
        asAccountWithGlobalRole(Roles.GLOBAL_ADMINISTRATOR) {
            run(applyQuery, mapOf("yaml" to initialYaml)) { data ->
                checkGraphQLUserErrors(data, "applyDashboards")
            }

            var exportedYaml: String? = null
            run("""{ userDashboards { name asYaml } }""") { data ->
                exportedYaml = data.path("userDashboards")
                    .find { it.getRequiredTextField("name") == name }
                    ?.getRequiredTextField("asYaml")
                    ?: fail("Dashboard '$name' not found")
            }

            val uuid = dashboardService.userDashboards().first { it.name == name }.uuid
            dashboardService.deleteDashboard(uuid)

            run(applyQuery, mapOf("yaml" to exportedYaml!!)) { data ->
                checkGraphQLUserErrors(data, "applyDashboards")
                val restored = data.path("applyDashboards").path("dashboards")
                assertNotNull(restored.find { it.getRequiredTextField("name") == name }, "Dashboard restored after round-trip")
            }
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