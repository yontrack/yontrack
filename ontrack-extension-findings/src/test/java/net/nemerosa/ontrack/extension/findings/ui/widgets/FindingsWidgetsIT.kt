package net.nemerosa.ontrack.extension.findings.ui.widgets

import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The findings widgets are offered to the dashboards.
 */
class FindingsWidgetsIT : AbstractQLKTITSupport() {

    @Test
    fun `Project findings widget`() {
        val widget = widget("extension/findings/ProjectFindings")
        assertEquals("Project findings", widget.path("name").asText())
        val config = widget.path("defaultConfig")
        assertTrue(config.path("project").isNull)
        assertEquals(false, config.path("showBranches").asBoolean())
    }

    @Test
    fun `Branch findings widget`() {
        val widget = widget("extension/findings/BranchFindings")
        assertEquals("Branch findings", widget.path("name").asText())
        val config = widget.path("defaultConfig")
        assertTrue(config.path("project").isNull)
        assertTrue(config.path("branch").isNull)
    }

    private fun widget(key: String): JsonNode =
        asUser().call {
            run(
                """
                    {
                        dashboardWidgets {
                            key
                            name
                            defaultConfig
                        }
                    }
                """
            ).path("dashboardWidgets").single { it.path("key").asText() == key }
        }
}
