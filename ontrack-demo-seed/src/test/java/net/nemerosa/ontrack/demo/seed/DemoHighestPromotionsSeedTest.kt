package net.nemerosa.ontrack.demo.seed

import com.fasterxml.jackson.databind.JsonNode
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The highest promotions per branch widget of the demo dashboard (#2006): an explicit branch, a
 * pattern entry, and a branch whose latest build is not promoted.
 */
class DemoHighestPromotionsSeedTest {

    private val clock = Clock.fixed(Instant.parse("2026-09-01T10:15:30Z"), ZoneOffset.UTC)

    private val target: InMemoryDemoTarget =
        InMemoryDemoTarget().also { DemoSeed(it, clock, log = {}).run(DemoContent.dataset(emptyList())) }

    private fun branch(project: String, branch: String): InMemoryDemoTarget.InMemoryBranch =
        target.projects()
            .filterIsInstance<InMemoryDemoTarget.InMemoryProject>()
            .single { it.name == project }
            .branches.single { it.name == branch }

    private fun latestBuildPromotions(project: String, branch: String): List<String> =
        branch(project, branch).builds.maxBy { it.creation }.promotions.map { it.first }

    private fun widget(uuid: String) = target.dashboards()
        .filterIsInstance<InMemoryDemoTarget.InMemoryDashboard>()
        .single().dashboard.widgets
        .single { it.uuid == uuid }

    private fun entries(config: JsonNode): List<Triple<String, String?, String?>> =
        config.path("branches").toList().map {
            Triple(
                it.path("project").asText(),
                it.path("branch").takeIf { node -> node.isTextual }?.asText(),
                it.path("pattern").takeIf { node -> node.isTextual }?.asText(),
            )
        }

    @Test
    fun `the demo dashboard shows the highest promotions of the branches of the service and of the UI`() {
        val widget = widget(DemoContent.HIGHEST_PROMOTIONS_WIDGET_UUID)

        assertEquals("home/HighestPromotionsPerBranch", widget.key)
        assertEquals("LADDER", widget.config.path("format").asText())
        // Two projects: the project stays shown, and the branches are sorted by name (#2110)
        assertTrue(widget.config.path("showProject").isMissingNode)
        assertEquals("NAME", widget.config.path("sort").asText())
        val entries = widget.config.path("branches").toList()
        assertEquals(
            listOf(
                Triple(DemoContent.SERVICE, DemoContent.MAIN, null),
                Triple(DemoContent.SERVICE, null, DemoContent.RELEASE_BRANCHES_PATTERN),
                Triple(DemoContent.UI, DemoContent.MAIN, null),
            ),
            entries(widget.config),
        )
        assertEquals(3, entries[1].path("count").asInt())
    }

    @Test
    fun `a second widget shows the branches of the service alone, without the project, by last activity`() {
        val widget = widget(DemoContent.HIGHEST_PROMOTIONS_SERVICE_WIDGET_UUID)

        assertEquals("home/HighestPromotionsPerBranch", widget.key)
        assertEquals("LADDER", widget.config.path("format").asText())
        assertFalse(widget.config.path("showProject").asBoolean(true))
        // The default sort, by last activity
        assertTrue(widget.config.path("sort").isMissingNode)
        assertEquals(
            listOf(
                Triple(DemoContent.SERVICE, DemoContent.MAIN, null),
                Triple(DemoContent.SERVICE, null, DemoContent.RELEASE_BRANCHES_PATTERN),
            ),
            entries(widget.config),
        )
    }

    @Test
    fun `the second widget is placed under the first one`() {
        val first = widget(DemoContent.HIGHEST_PROMOTIONS_WIDGET_UUID).layout
        val second = widget(DemoContent.HIGHEST_PROMOTIONS_SERVICE_WIDGET_UUID).layout
        assertEquals(first.y + first.h, second.y)
    }

    @Test
    fun `the pattern of the widget brings the maintenance branch, whose latest build is not promoted`() {
        assertTrue(Regex(DemoContent.RELEASE_BRANCHES_PATTERN).matches(DemoContent.MAINTENANCE))
        assertEquals(emptyList(), latestBuildPromotions(DemoContent.SERVICE, DemoContent.MAINTENANCE))
    }

    @Test
    fun `the latest build of the main branch of the service is granted every level of its branch`() {
        assertEquals(
            branch(DemoContent.SERVICE, DemoContent.MAIN).promotionLevels.toSet(),
            latestBuildPromotions(DemoContent.SERVICE, DemoContent.MAIN).toSet(),
        )
    }

    @Test
    fun `the latest build of the UI is at BRONZE, without SILVER, for the ladder to show a level not granted`() {
        assertEquals(listOf(DemoContent.BRONZE), latestBuildPromotions(DemoContent.UI, DemoContent.MAIN))
    }
}
