package net.nemerosa.ontrack.demo.seed

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The last builds widget of the demo dashboard: the main branch of the service, narrowed to a few
 * promotions and validations.
 */
class DemoLastBuildsSeedTest {

    private val clock = Clock.fixed(Instant.parse("2026-09-01T10:15:30Z"), ZoneOffset.UTC)

    private val target: InMemoryDemoTarget =
        InMemoryDemoTarget().also { DemoSeed(it, clock, log = {}).run(DemoContent.dataset(emptyList())) }

    @Test
    fun `the demo dashboard shows the last builds of the main branch of the service`() {
        val widget = target.dashboards()
            .filterIsInstance<InMemoryDemoTarget.InMemoryDashboard>()
            .single().dashboard.widgets
            .single { it.uuid == DemoContent.LAST_BUILDS_WIDGET_UUID }

        assertEquals("home/LastBuildsForBranch", widget.key)
        val config = widget.config
        assertEquals(DemoContent.SERVICE, config.path("project").asString())
        assertEquals(DemoContent.MAIN, config.path("branch").asString())
        assertEquals(5, config.path("count").asInt())
        assertEquals(
            listOf(DemoContent.SILVER, DemoContent.GOLD),
            config.path("promotions").toList().map { it.asString() },
        )
        assertEquals(
            listOf(DemoContent.BUILD, DemoContent.UNIT_TESTS, DemoContent.INTEGRATION_TESTS),
            config.path("validations").toList().map { it.asString() },
        )
    }
}
