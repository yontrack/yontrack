package net.nemerosa.ontrack.kdsl.acceptance.tests.ha

import net.nemerosa.ontrack.kdsl.acceptance.tests.support.uid
import net.nemerosa.ontrack.kdsl.acceptance.tests.support.waitUntil
import net.nemerosa.ontrack.kdsl.spec.Ontrack
import net.nemerosa.ontrack.kdsl.spec.extension.scorecard.recomputeScorecard
import net.nemerosa.ontrack.kdsl.spec.extension.scorecard.scorecard
import net.nemerosa.ontrack.kdsl.spec.jobs.JobRun
import net.nemerosa.ontrack.kdsl.spec.jobs.JobStatus
import net.nemerosa.ontrack.kdsl.spec.jobs.jobs
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The jobs run in Quartz, clustered through the database (#2077): one run per period in the
 * cluster, pause and resume stored and seen by every replica, the same job ID on every replica.
 *
 * Uses the probe jobs of the stack (`ontrack.config.jobs.testing.enabled`): a periodic one, every
 * [PERIOD], and a manual one.
 */
class ACCHAJobs : AbstractACCHATestSupport() {

    @Test
    fun `a periodic job runs once per period across both backends`() {
        withPeriodicRunning {
            // Leaves the job a few periods to run
            Thread.sleep(WINDOW.toMillis())
            val runs = runs(ontrackA, PERIODIC, now().minus(WINDOW), now())
            val expected = WINDOW.toMillis() / PERIOD.toMillis()
            assertTrue(
                runs.size in (expected - 2)..(expected + 1),
                "About one run per period in the cluster: expected $expected, got ${runs.size}: $runs"
            )
            runs.zipWithNext().forEach { (previous, next) ->
                assertTrue(
                    Duration.between(previous.startedAt, next.startedAt) >= PERIOD.dividedBy(2),
                    "No two runs in the same period: $previous then $next"
                )
            }
        }
    }

    @Test
    fun `a job paused through A does not run on B, and resumed through B runs again`() {
        val id = status(ontrackA, PERIODIC).id
        ontrackA.jobs.pause(id)
        try {
            assertTrue(status(ontrackB, PERIODIC).paused, "The job is paused through B")
            assertNoRunWhilePaused()
        } finally {
            ontrackB.jobs.resume(id)
        }
        assertFalse(status(ontrackA, PERIODIC).paused, "The job is resumed through A")
        assertRunsAgain()
    }

    @Test
    fun `all jobs paused through A do not run on B, and resumed through B run again`() {
        withPeriodicRunning {
            ontrackA.jobs.pauseAll()
            try {
                assertTrue(ontrackB.jobs.paused, "All jobs are paused through B")
                assertNoRunWhilePaused()
            } finally {
                ontrackB.jobs.resumeAll()
            }
            assertFalse(ontrackA.jobs.paused, "All jobs are resumed through A")
            assertRunsAgain()
        }
    }

    @Test
    fun `run now through B runs the job once, on either backend`() {
        val start = now().minusSeconds(1)
        val id = status(ontrackB, MANUAL).id
        val before = runs(ontrackA, MANUAL, start, far()).size
        ontrackB.jobs.runNow(id)
        waitUntil(timeout = 30_000L, interval = 500L, task = "The manual job has run") {
            runs(ontrackA, MANUAL, start, far()).size > before
        }
        // Leaves time for a second run, which must not happen
        Thread.sleep(PERIOD.multipliedBy(3).toMillis())
        assertEquals(before + 1, runs(ontrackB, MANUAL, start, far()).size, "One entry in the history for one run")
    }

    @Test
    fun `a job has the same ID through A and through B, and pausing it by this ID through B pauses it`() {
        listOf(PERIODIC, MANUAL).forEach { key ->
            assertEquals(status(ontrackA, key).id, status(ontrackB, key).id, "Same ID for the $key job")
        }
        val idsA = ontrackA.jobs.list().associate { "${it.category}/${it.type}/${it.key}" to it.id }
        val idsB = ontrackB.jobs.list().associate { "${it.category}/${it.type}/${it.key}" to it.id }
        assertEquals(idsA, idsB, "Same jobs and IDs through A and through B")

        val id = status(ontrackA, PERIODIC).id
        ontrackB.jobs.pause(id)
        try {
            val paused = ontrackA.jobs.status(id)
            assertNotNull(paused, "Job $id through A") {
                assertEquals(PERIODIC, it.key, "Job $id is the periodic job")
                assertTrue(it.paused, "The job paused through B is paused through A")
            }
        } finally {
            ontrackB.jobs.resume(id)
        }
    }

    @Test
    fun `a scorecard recompute requested through A runs whichever backend fires it`() {
        val name = uid("p")
        val projectA = ontrackA.createProject(name, "")
        projectA.createBranch("main", "")
        val projectB = ontrackB.findProjectByName(name) ?: fail("Project $name through B")
        val before = projectB.scorecard().sets.associate { it.name to it.computedAt }

        projectA.recomputeScorecard()

        waitUntil(timeout = 60_000L, interval = 1_000L, task = "The readings of $name are recomputed") {
            val sets = projectB.scorecard().sets
            sets.isNotEmpty() && sets.all { set ->
                val computedAt = set.computedAt
                computedAt != null && (before[set.name]?.let { computedAt.isAfter(it) } ?: true)
            }
        }
    }

    @Test
    fun `the search documents are rebuilt once at startup when both backends start together`() {
        val rebuilds = ontrackA.jobs.list(category = "search", type = "startup-rebuild")
        assertTrue(rebuilds.isNotEmpty(), "Startup rebuild jobs are listed")
        rebuilds.forEach { job ->
            val runs = ontrackB.jobs.history(job.category, job.type, job.key, now().minusDays(1), far())
            assertEquals(1, runs.size, "Search documents of ${job.key} rebuilt once at startup: $runs")
        }
    }

    /**
     * Makes sure the periodic job is not paused before running [code].
     */
    private fun withPeriodicRunning(code: () -> Unit) {
        if (ontrackA.jobs.paused) {
            ontrackA.jobs.resumeAll()
        }
        val periodic = status(ontrackA, PERIODIC)
        if (periodic.paused) {
            ontrackA.jobs.resume(periodic.id)
        }
        assertRunsAgain()
        code()
    }

    /**
     * Once any run in progress is over, no run of the periodic job for a few periods.
     */
    private fun assertNoRunWhilePaused() {
        Thread.sleep(PERIOD.multipliedBy(2).toMillis())
        val from = now()
        Thread.sleep(PERIOD.multipliedBy(4).toMillis())
        val runs = runs(ontrackB, PERIODIC, from, far())
        assertTrue(runs.isEmpty(), "No run while paused: $runs")
    }

    private fun assertRunsAgain() {
        val from = now()
        waitUntil(timeout = 30_000L, interval = 500L, task = "The periodic job runs") {
            runs(ontrackA, PERIODIC, from, far()).isNotEmpty()
        }
    }

    private fun status(ontrack: Ontrack, key: String): JobStatus =
        ontrack.jobs.status(CATEGORY, TYPE, key) ?: fail("Probe job $key not found")

    private fun runs(ontrack: Ontrack, key: String, from: LocalDateTime, to: LocalDateTime): List<JobRun> =
        ontrack.jobs.history(CATEGORY, TYPE, key, from, to)

    private fun now(): LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)

    private fun far(): LocalDateTime = now().plusHours(1)

    companion object {
        private const val CATEGORY = "testing"
        private const val TYPE = "probe"
        private const val PERIODIC = "periodic"
        private const val MANUAL = "manual"

        /**
         * Period of the periodic probe job, `ontrack.config.jobs.testing.period` in
         * `compose/docker-compose-kdsl-ha.yml`
         */
        private val PERIOD: Duration = Duration.ofSeconds(2)

        private val WINDOW: Duration = PERIOD.multipliedBy(10)
    }
}
