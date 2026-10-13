package net.nemerosa.ontrack.kdsl.acceptance.tests.ha

import net.nemerosa.ontrack.kdsl.acceptance.tests.ACCProperties
import net.nemerosa.ontrack.kdsl.acceptance.tests.support.waitUntil
import net.nemerosa.ontrack.kdsl.spec.Ontrack
import net.nemerosa.ontrack.kdsl.spec.jobs.JobStatus
import net.nemerosa.ontrack.kdsl.spec.jobs.jobs
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The status of the jobs is shared by all the replicas (#2079): the same through A and through B,
 * naming the backend a job runs on; a stop and a "run now" work whichever backend runs the job;
 * a backend killed during a run does not leave its job running.
 *
 * Uses the slow probe job of the stack (`ontrack.config.jobs.testing.enabled`), which runs until it
 * is stopped. The host names of the backends, `ontrack-a` and `ontrack-b`, are the IDs of their
 * Quartz instances.
 */
class ACCHAJobStatus : AbstractACCHATestSupport() {

    @Test
    fun `the status of a running job is the same through A and through B, naming the backend running it`() {
        withSlowRun { runningOn ->
            val throughA = status(ontrackA)
            val throughB = status(ontrackB)
            assertEquals(throughA, throughB, "Same status through A and B")
            assertTrue(throughA.running, "Running")
            assertEquals("RUNNING", throughA.state)
            assertEquals(runningOn, throughA.runningOn, "Running on $runningOn")
            assertNotNull(throughA.runningSince, "Running since")
            assertEquals("Probe job $SLOW: running until stopped", throughA.progress, "Progress of the run")
            // The jobs which do not run on their own are listed the same
            assertEquals(
                ontrackA.jobs.list(CATEGORY, TYPE).filter { it.key != PERIODIC },
                ontrackB.jobs.list(CATEGORY, TYPE).filter { it.key != PERIODIC },
                "Same list through A and B",
            )
        }
    }

    @Test
    fun `a job stopped through one backend is interrupted on the other`() {
        withSlowRun { backend ->
            val other = other(backend)
            val runs = status(other).runCount
            other.jobs.stop(status(other).id)
            // The backend which ran the job knows at once, the other one once Quartz removes the fire
            waitUntil(timeout = 30_000L, interval = 500L, task = "The job is stopped") {
                !status(ontrackA).running && !status(ontrackB).running
            }
            with(status(other)) {
                assertNull(runningOn, "Running nowhere")
                assertEquals("", progress, "No progress")
                assertNotNull(lastError, "The stopped run failed")
                assertEquals(runs, runCount, "No other run")
            }
        }
    }

    @Test
    fun `run now through one backend while the job runs on the other answers already running`() {
        withSlowRun { runningOn ->
            val id = status(ontrackA).id
            assertEquals("ALREADY_RUNNING", other(runningOn).jobs.runNow(id), "Running on the other backend")
            assertEquals("ALREADY_RUNNING", backend(runningOn).jobs.runNow(id), "Running on this backend")
        }
    }

    @Test
    fun `a backend killed during a run does not leave its job running`() {
        val project = ACCProperties.HA.project
        assumeTrue(project != null, "The Compose project of the stack is needed to kill a backend")
        withSlowRun { runningOn ->
            val container = "$project-$runningOn-1"
            docker("kill", container)
            try {
                // Quartz on the other backend finds the killed one failed once it stops checking
                // in, and removes its fires
                val survivor = other(runningOn)
                waitUntil(timeout = 120_000L, interval = 1_000L, task = "The job of the killed backend is no longer running") {
                    !status(survivor).running
                }
                with(status(survivor)) {
                    assertNull(this.runningOn, "Running nowhere")
                    assertEquals("", progress, "No progress shown")
                }
            } finally {
                docker("start", container)
                waitForHealth(runningOn)
            }
        }
    }

    /**
     * Runs the slow probe job, and waits for it to run on either backend. Once [code] is done, the
     * job is stopped if it still runs.
     *
     * @param code Given the host name of the backend running the job
     */
    private fun withSlowRun(code: (runningOn: String) -> Unit) {
        val id = status(ontrackA).id
        stopAndWait(id)
        assertEquals("TRIGGERED", ontrackA.jobs.runNow(id), "The slow job is triggered")
        var runningOn: String? = null
        waitUntil(timeout = 30_000L, interval = 500L, task = "The slow job runs") {
            runningOn = status(ontrackB).runningOn
            runningOn != null
        }
        val on = runningOn ?: fail("Not running")
        assertTrue(on in setOf(A, B), "Running on one of the two backends: $on")
        try {
            code(on)
        } finally {
            stopAndWait(id)
        }
    }

    private fun stopAndWait(id: Int) {
        if (status(ontrackA).running) {
            ontrackA.jobs.stop(id)
        }
        // The backend which ran the job knows at once, the other one once Quartz removes the fire
        waitUntil(timeout = 30_000L, interval = 500L, task = "The slow job is stopped") {
            !status(ontrackA).running && !status(ontrackB).running
        }
    }

    private fun backend(hostName: String): Ontrack = if (hostName == A) ontrackA else ontrackB

    private fun other(hostName: String): Ontrack = if (hostName == A) ontrackB else ontrackA

    private fun status(ontrack: Ontrack): JobStatus =
        ontrack.jobs.status(CATEGORY, TYPE, SLOW) ?: fail("Probe job $SLOW not found")

    private fun docker(vararg args: String) {
        val process = ProcessBuilder(listOf("docker") + args).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        assertTrue(process.waitFor(60, TimeUnit.SECONDS), "docker ${args.joinToString(" ")} is over")
        assertEquals(0, process.exitValue(), "docker ${args.joinToString(" ")}: $output")
    }

    private fun waitForHealth(hostName: String) {
        val url = if (hostName == A) ACCProperties.HA.A.Mgt.url else ACCProperties.HA.B.Mgt.url
        val client = HttpClient.newHttpClient()
        waitUntil(timeout = 300_000L, interval = 2_000L, task = "Backend $hostName is up again") {
            try {
                client.send(
                    HttpRequest.newBuilder(URI("$url/health")).GET().build(),
                    HttpResponse.BodyHandlers.discarding(),
                ).statusCode() == 200
            } catch (_: Exception) {
                false
            }
        }
    }

    companion object {
        private const val CATEGORY = "testing"
        private const val TYPE = "probe"
        private const val PERIODIC = "periodic"
        private const val SLOW = "slow"

        /**
         * Host names of the backends, in `compose/docker-compose-kdsl-ha.yml`
         */
        private const val A = "ontrack-a"
        private const val B = "ontrack-b"
    }
}
