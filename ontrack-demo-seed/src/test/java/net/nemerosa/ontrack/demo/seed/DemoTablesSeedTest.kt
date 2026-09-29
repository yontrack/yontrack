package net.nemerosa.ontrack.demo.seed

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The project of the demo whose tables are long enough to scroll (#1932): sticky table headers
 * are only visible on a table which overflows what it is shown in, and the rest of the demo is
 * too small for most of its tables to.
 */
class DemoTablesSeedTest {

    private val clock = Clock.fixed(Instant.parse("2026-09-01T10:15:30Z"), ZoneOffset.UTC)

    private fun seeded(): InMemoryDemoTarget =
        InMemoryDemoTarget().also { DemoSeed(it, clock, log = {}).run(DemoContent.dataset(emptyList())) }

    private fun InMemoryDemoTarget.e2e(): InMemoryDemoTarget.InMemoryProject =
        projects().filterIsInstance<InMemoryDemoTarget.InMemoryProject>().single { it.name == DemoContent.E2E }

    private fun InMemoryDemoTarget.e2eMain(): InMemoryDemoTarget.InMemoryBranch =
        e2e().branches.single { it.name == DemoContent.MAIN }

    /** The branch matrix scrolls down after "Load more", and sideways on a wide screen. */
    @Test
    fun `the main branch has about thirty builds and thirty validation stamps`() {
        val main = seeded().e2eMain()

        assertTrue(main.builds.size >= 30, "Enough builds to scroll the branch matrix")
        assertTrue(main.validationStamps.size >= 30, "Enough stamps to scroll the branch matrix sideways")
    }

    /** The history of a validation stamp scrolls after a few "Load more". */
    @Test
    fun `one validation stamp is run on every build`() {
        val main = seeded().e2eMain()

        main.builds.forEach { build ->
            assertEquals(
                1,
                build.validations.count { it.stamp == DemoContent.E2E_SMOKE },
                "Build ${build.name} is validated on ${DemoContent.E2E_SMOKE}",
            )
        }
    }

    /** The validations of the build page scroll. */
    @Test
    fun `the latest build carries a run of every validation stamp`() {
        val main = seeded().e2eMain()

        val latest = main.builds.last()
        assertEquals(main.validationStamps.toSet(), latest.validations.map { it.stamp }.toSet())
    }

    /** The branch statuses widget scrolls. */
    @Test
    fun `the demo dashboard has a branch statuses widget listing every branch of the project`() {
        val target = seeded()

        val widget = target.dashboards()
            .filterIsInstance<InMemoryDemoTarget.InMemoryDashboard>()
            .single().dashboard.widgets
            .single { it.uuid == DemoContent.E2E_WIDGET_UUID }
        assertEquals("home/BranchStatuses", widget.key)

        val listed = widget.config.path("branches").values().map { it.path("project").asString() to it.path("branch").asString() }
        val branches = target.e2e().branches.map { DemoContent.E2E to it.name }
        assertTrue(listed.size >= 15, "Enough branches to overflow the widget")
        assertEquals(branches.toSet(), listed.toSet(), "The widget lists the branches of the project, and only them")
    }
}
