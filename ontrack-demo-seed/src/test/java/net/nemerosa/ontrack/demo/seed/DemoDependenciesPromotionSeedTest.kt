package net.nemerosa.ontrack.demo.seed

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The promotion of the dependencies of a build in the demo (#2146): GOLD on the aggregate fans out to
 * one unqualified link, already GOLD, and to two qualified links to the same project.
 */
class DemoDependenciesPromotionSeedTest {

    private val clock = Clock.fixed(Instant.parse("2026-09-01T10:15:30Z"), ZoneOffset.UTC)

    private fun seeded(): InMemoryDemoTarget =
        InMemoryDemoTarget().also { DemoSeed(it, clock, log = {}).run(DemoContent.dataset(emptyList())) }

    private fun InMemoryDemoTarget.build(project: String, name: String): InMemoryDemoTarget.InMemoryBuild =
        projects()
            .map { it as InMemoryDemoTarget.InMemoryProject }
            .single { it.name == project }
            .branches.single { it.name == DemoContent.MAIN }
            .builds.single { it.name == name }

    private fun InMemoryDemoTarget.InMemoryBuild.goldRuns() = promotions.count { it.first == DemoContent.GOLD }

    @Test
    fun `the aggregate links to its components, qualified or not`() {
        val suite = seeded().build(DemoContent.SUITE, "10")

        assertEquals(
            listOf(
                "${DemoContent.SUITE_CORE}/1.2" to "",
                "${DemoContent.SUITE_MODULES}/1.3" to DemoContent.SUITE_MODULE_BILLING,
                "${DemoContent.SUITE_MODULES}/1.4" to DemoContent.SUITE_MODULE_VISITS,
            ),
            suite.links.map { "${it.branch.project.name}/${it.name}" }.zip(suite.linkQualifiers),
        )
    }

    @Test
    fun `the workflow of the aggregate succeeds and promotes the qualified links`() {
        val target = seeded()

        val run = target.build(DemoContent.SUITE, "10").workflowRuns.getValue(DemoContent.GOLD)

        assertEquals(WorkflowOutcome.SUCCESS, run.outcome, "Errors: ${run.errors}")
        assertEquals(1, target.build(DemoContent.SUITE_MODULES, "1.3").goldRuns())
        assertEquals(1, target.build(DemoContent.SUITE_MODULES, "1.4").goldRuns())
    }

    @Test
    fun `the component already promoted is skipped`() {
        assertEquals(1, seeded().build(DemoContent.SUITE_CORE, "1.2").goldRuns())
    }
}
