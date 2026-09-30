package net.nemerosa.ontrack.extension.scorecard.ui.widgets

import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProjectScorecardWidgetTest {

    private val widget = ProjectScorecardWidget()

    @Test
    fun `the key locates the UI components of the widget`() {
        assertEquals("extension/scorecard/ProjectScorecard", widget.key)
        assertEquals("Project scorecard", widget.name)
        assertEquals("Targets met by a project in each of its estates, and its readings", widget.description)
    }

    @Test
    fun `no project and the default set by default`() {
        assertNull(widget.defaultConfig.project)
        assertNull(widget.defaultConfig.set)
    }

    @Test
    fun `the set is optional in the configuration`() {
        val config = mapOf("project" to "petclinic-visits").asJson().parse<ProjectScorecardWidgetConfig>()
        assertEquals(ProjectScorecardWidgetConfig(project = "petclinic-visits", set = null), config)
    }

    @Test
    fun `the set is the Project set or an estate`() {
        assertEquals(
            ProjectScorecardWidgetConfig(project = "petclinic-visits", set = "project"),
            mapOf("project" to "petclinic-visits", "set" to "project").asJson().parse<ProjectScorecardWidgetConfig>(),
        )
        assertEquals(
            ProjectScorecardWidgetConfig(project = "petclinic-visits", set = "Demo products"),
            mapOf("project" to "petclinic-visits", "set" to "Demo products").asJson().parse<ProjectScorecardWidgetConfig>(),
        )
    }
}
