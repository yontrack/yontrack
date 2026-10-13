package net.nemerosa.ontrack.service.job

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.it.AbstractServiceTestSupport
import net.nemerosa.ontrack.it.jobs.OtherReplica
import net.nemerosa.ontrack.it.jobs.TestJobSupplier
import net.nemerosa.ontrack.it.waitUntil
import net.nemerosa.ontrack.job.*
import net.nemerosa.ontrack.model.job.JobHistoryItemStatus
import net.nemerosa.ontrack.model.job.JobHistoryService
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.quartz.Scheduler
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.jvm.optionals.getOrNull
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

/**
 * The status of the jobs, shared by all the replicas (#2079): the runs, progress and counters of a
 * job are in `JOBS`, whether it runs and where comes from the fires Quartz executes, and a stop or
 * a "run now" works whichever replica runs the job.
 *
 * The other replicas are played by [OtherReplica], which writes to the tables of Quartz what a
 * replica executing a job would. The jobs run on the threads of Quartz, in transactions of their
 * own: these tests are not transactional.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@OptIn(ExperimentalTime::class)
class QuartzJobStatusIT : AbstractServiceTestSupport() {

    @Autowired
    private lateinit var jobScheduler: JobScheduler

    @Autowired
    private lateinit var testJobSupplier: TestJobSupplier

    @Autowired
    private lateinit var jobHistoryService: JobHistoryService

    @Autowired
    private lateinit var quartz: Scheduler

    private val scheduled = mutableListOf<JobKey>()

    @AfterEach
    fun cleanup() {
        scheduled.forEach { key ->
            jobScheduler.stop(key)
            testJobSupplier.unschedule(jobScheduler, key)
        }
    }

    /**
     * Job reporting its progress, then waiting for [duration] - or until stopped - and failing
     * when [failure] is set.
     */
    private class TestJob(
        duration: Duration = Duration.ZERO,
    ) : Job {
        val runs = AtomicInteger()
        val duration = AtomicReference(duration)
        val failure = AtomicReference<String?>()
        private val key = JobCategory.of("test").withName("Test").getType("status").withName("Status").getKey(uid("j-"))
        override fun getKey(): JobKey = key
        override fun getTask() = JobRun { listener ->
            runs.incrementAndGet()
            listener.progress(JobRunProgress(50, "Halfway"))
            val wait = duration.get()
            if (!wait.isZero) {
                Thread.sleep(wait.toMillis())
            }
            failure.get()?.let { throw IllegalStateException(it) }
        }

        override fun getDescription(): String = "Status job ${key.id}"
        override fun isDisabled(): Boolean = false
    }

    private fun schedule(job: TestJob): TestJob {
        testJobSupplier.schedule(jobScheduler, job, Schedule.NONE)
        scheduled += job.key
        return job
    }

    private fun status(job: Job): JobStatus =
        jobScheduler.getJobStatus(job.key).getOrNull() ?: error("No status for ${job.key}")

    private fun runNow(job: Job) {
        val launch = jobScheduler.launch(job.key)
        assertEquals(JobLaunchStatus.TRIGGERED, launch.status, "Job triggered")
        assertNotNull(launch.future, "Run to wait for").get(30, TimeUnit.SECONDS)
    }

    private fun waitRunning(job: Job) {
        waitUntil("The job runs", interval = 100.milliseconds, timeout = 30.seconds) {
            status(job).isRunning
        }
    }

    private fun column(job: Job, column: String): Any? =
        namedParameterJdbcTemplate.queryForObject(
            "SELECT $column FROM JOBS WHERE JOB_CATEGORY = :category AND JOB_TYPE = :type AND JOB_KEY = :key",
            MapSqlParameterSource()
                .addValue("category", job.key.type.category.key)
                .addValue("type", job.key.type.key)
                .addValue("key", job.key.id),
            Any::class.java,
        )

    private fun requestStopAsAnotherReplica(job: Job) {
        namedParameterJdbcTemplate.update(
            "UPDATE JOBS SET STOP_REQUESTED = TRUE WHERE JOB_CATEGORY = :category AND JOB_TYPE = :type AND JOB_KEY = :key",
            MapSqlParameterSource()
                .addValue("category", job.key.type.category.key)
                .addValue("type", job.key.type.key)
                .addValue("key", job.key.id),
        )
    }

    @Test
    fun `The runs of a job are counted in the database`() {
        val job = schedule(TestJob(duration = Duration.ofMillis(100)))
        val before = Time.now
        runNow(job)
        runNow(job)
        with(status(job)) {
            assertFalse(isRunning, "Not running")
            assertEquals(2, runCount, "Two runs")
            assertNotNull(lastRunDate, "Last run") {
                assertTrue(!it.isBefore(before.withNano(0)), "Last run after the start of the test")
            }
            assertTrue(lastRunDurationMs >= 100, "Duration of the last run")
            assertNull(lastError, "No error")
            assertNull(progress, "No progress once over")
        }
        assertEquals(2L, (column(job, "RUN_COUNT") as Number).toLong(), "Runs counted in JOBS")
    }

    @Test
    fun `The error of a failed run is recorded, and cleared by a successful run`() {
        val job = schedule(TestJob())
        job.failure.set("Some failure")
        runNow(job)
        runNow(job)
        with(status(job)) {
            assertEquals("Some failure", lastError)
            assertEquals(2, lastErrorCount, "Two errors in a row")
            assertTrue(isError, "In error")
        }
        assertEquals("Some failure", column(job, "LAST_ERROR"), "Error in JOBS")
        job.failure.set(null)
        runNow(job)
        with(status(job)) {
            assertNull(lastError, "Error cleared")
            assertEquals(0, lastErrorCount, "No more error")
            assertFalse(isError, "No longer in error")
            assertEquals(3, runCount, "Three runs")
        }
    }

    @Test
    fun `A job running on this replica is shown running on it, with its progress`() {
        val job = schedule(TestJob(duration = Duration.ofSeconds(30)))
        val before = Time.now.withNano(0)
        val run = jobScheduler.launch(job.key).future
        assertNotNull(run, "Job fired")
        waitRunning(job)
        with(status(job)) {
            assertEquals(quartz.schedulerInstanceId, runningOn, "Running on this replica")
            assertNotNull(runningSince, "Running since") {
                assertTrue(!it.isBefore(before), "Running since its fire")
            }
            assertEquals(JobState.RUNNING, state)
            assertTrue(canBeStopped(), "Can be stopped")
        }
        waitUntil("The progress is shared", interval = 100.milliseconds, timeout = 10.seconds) {
            status(job).progress == JobRunProgress(50, "Halfway")
        }
        assertTrue(jobScheduler.stop(job.key), "Stopped")
        run.get(10, TimeUnit.SECONDS)
        with(status(job)) {
            assertFalse(isRunning, "No longer running")
            assertNull(runningOn, "Running nowhere")
            assertNull(runningSince, "Not running")
            assertNull(progress, "No progress")
        }
    }

    @Test
    fun `A job running on another replica is shown running there, until Quartz finds the run over`() {
        val job = schedule(TestJob())
        OtherReplica(namedParameterJdbcTemplate).use { other ->
            val since = System.currentTimeMillis() - 60_000
            other.run(job.key, since)
            with(status(job)) {
                assertTrue(isRunning, "Running")
                assertEquals(other.instance, runningOn, "Running on the other replica")
                assertEquals(Time.from(since), runningSince, "Running since its fire")
                assertEquals(JobState.RUNNING, state)
            }
            assertTrue(other.instance in jobScheduler.jobStatuses.filter { it.isRunning }.mapNotNull { it.runningOn })
            // The replica stops, and Quartz recovers its fires
            other.done()
            with(status(job)) {
                assertFalse(isRunning, "No longer running")
                assertNull(runningOn, "Running nowhere")
            }
        }
    }

    @Test
    fun `The progress of a run lost with its replica is not shown`() {
        val job = schedule(TestJob())
        namedParameterJdbcTemplate.update(
            "UPDATE JOBS SET PROGRESS_PERCENTAGE = 10, PROGRESS_MESSAGE = 'Lost' WHERE JOB_KEY = :key",
            MapSqlParameterSource("key", job.key.id),
        )
        OtherReplica(namedParameterJdbcTemplate).use { other ->
            other.run(job.key)
            assertEquals(JobRunProgress(10, "Lost"), status(job).progress, "Progress of the run")
        }
        assertNull(status(job).progress, "No progress once the run is gone")
    }

    @Test
    fun `Run now answers already running when the job runs on another replica`() {
        val job = schedule(TestJob())
        OtherReplica(namedParameterJdbcTemplate).use { other ->
            other.run(job.key)
            val launch = jobScheduler.launch(job.key)
            assertEquals(JobLaunchStatus.ALREADY_RUNNING, launch.status)
            assertEquals(other.instance, launch.runningOn, "Running on the other replica")
            assertNull(launch.future, "Nothing to wait for")
            assertNull(jobScheduler.fireImmediately(job.key).getOrNull(), "Not fired")
            other.done()
        }
        runNow(job)
        assertEquals(1, job.runs.get(), "Run once, once the other run is over")
    }

    @Test
    fun `Run now answers already running when the job runs on this replica`() {
        val job = schedule(TestJob(duration = Duration.ofSeconds(30)))
        val run = jobScheduler.launch(job.key).future
        assertNotNull(run, "Job fired")
        waitRunning(job)
        val launch = jobScheduler.launch(job.key)
        assertEquals(JobLaunchStatus.ALREADY_RUNNING, launch.status)
        assertEquals(quartz.schedulerInstanceId, launch.runningOn, "Running on this replica")
        jobScheduler.stop(job.key)
        run.get(10, TimeUnit.SECONDS)
    }

    @Test
    fun `A disabled job cannot be run now`() {
        val job = object : Job {
            private val key = JobCategory.of("test").withName("Test").getType("status").withName("Status").getKey(uid("d-"))
            override fun getKey(): JobKey = key
            override fun getTask() = JobRun {}
            override fun getDescription(): String = "Disabled job"
            override fun isDisabled(): Boolean = true
        }
        testJobSupplier.schedule(jobScheduler, job, Schedule.NONE)
        scheduled += job.key
        assertEquals(JobLaunchStatus.NOT_RUNNABLE, jobScheduler.launch(job.key).status)
    }

    @Test
    fun `Stopping a job running on another replica requests its stop`() {
        val job = schedule(TestJob())
        assertFalse(jobScheduler.stop(job.key), "Not running, nothing to stop")
        assertEquals(false, column(job, "STOP_REQUESTED"), "No stop requested")
        OtherReplica(namedParameterJdbcTemplate).use { other ->
            other.run(job.key)
            assertTrue(jobScheduler.stop(job.key), "Stop requested")
            assertEquals(true, column(job, "STOP_REQUESTED"), "Stop requested in JOBS")
        }
    }

    @Test
    fun `A run whose stop is requested by another replica is interrupted`() {
        val job = schedule(TestJob(duration = Duration.ofSeconds(60)))
        val start = Time.now.minusSeconds(1)
        val run = jobScheduler.launch(job.key).future
        assertNotNull(run, "Job fired")
        waitRunning(job)
        requestStopAsAnotherReplica(job)
        // Checked every 2 seconds
        run.get(10, TimeUnit.SECONDS)
        with(status(job)) {
            assertFalse(isRunning, "Stopped")
            assertNotNull(lastError, "Stopped with an error")
        }
        assertEquals(false, column(job, "STOP_REQUESTED"), "Stop request cleared once the run is over")
        val history = jobHistoryService.getHistory(job.key, start, Time.now.plusMinutes(1), skipErrors = false)
        assertEquals(listOf(JobHistoryItemStatus.ERROR), history.map { it.status }, "The stopped run is in error")
        // The next run is not stopped
        job.duration.set(Duration.ofMillis(100))
        runNow(job)
        assertNull(status(job).lastError, "Next run not stopped")
    }
}
