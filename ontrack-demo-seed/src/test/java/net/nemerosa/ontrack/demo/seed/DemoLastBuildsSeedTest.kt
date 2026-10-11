package net.nemerosa.ontrack.demo.seed

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The last builds widget of the demo dashboard: the main branch of the sample service, with the
 * promotions and validations the branch statuses widget shows.
 */
class DemoLastBuildsSeedTest {

    private val clock = Clock.fixed(Instant.parse("2026-09-01T10:15:30Z"), ZoneOffset.UTC)

    private fun seeded(): InMemoryDemoTarget =
        InMemoryDemoTarget().also { DemoSeed(it, clock, log = {}).run(DemoContent.dataset(emptyList())) }

    @Test
    fun `the demo dashboard shows the last builds of the main branch of the service`() {
        val widget = seeded().dashboards()
            .filterIsInstance<InMemoryDemoTarget.InMemoryDashboard>()
            .single().dashboard.widgets
            .single { it.uuid == DemoContent.LAST_BUILDS_WIDGET_UUID }

        assertEquals("home/LastBuildsForBranch", widget.key)
        assertEquals(DemoContent.SERVICE, widget.config.path("project").asString())
        assertEquals(DemoContent.MAIN, widget.config.path("branch").asString())
        assertEquals(5, widget.config.path("count").asInt())
        assertEquals(
            listOf(DemoContent.SILVER, DemoContent.GOLD),
            widget.config.path("promotions").toList().map { it.asString() },
        )
        assertEquals(
            listOf(DemoContent.BUILD, DemoContent.UNIT_TESTS, DemoContent.INTEGRATION_TESTS),
            widget.config.path("validations").toList().map { it.asString() },
        )
    }
}
