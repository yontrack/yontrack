package net.nemerosa.ontrack.service.job

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.it.AbstractServiceTestSupport
import net.nemerosa.ontrack.it.jobs.TestJobSupplier
import net.nemerosa.ontrack.it.waitUntil
import net.nemerosa.ontrack.job.*
import net.nemerosa.ontrack.job.support.JobNotScheduledException
import net.nemerosa.ontrack.model.job.JobHistoryService
import net.nemerosa.ontrack.model.support.SettingsRepository
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.support.CronExpression
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.jvm.optionals.getOrNull
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

/**
 * The job scheduler, on Quartz with its clustered JDBC store (#2077). The jobs run on the threads of
 * Quartz, in transactions of their own: these tests are not transactional.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@OptIn(ExperimentalTime::class)
class QuartzJobSchedulerIT : AbstractServiceTestSupport() {

    @Autowired
    private lateinit var jobScheduler: JobScheduler

    @Autowired
    private lateinit var testJobSupplier: TestJobSupplier

    @Autowired
    private lateinit var jobHistoryService: JobHistoryService

    @Autowired
    private lateinit var settingsRepository: SettingsRepository

    private val scheduled = mutableListOf<JobKey>()

    @AfterEach
    fun cleanup() {
        if (jobScheduler.isPaused) {
            jobScheduler.resume()
        }
        scheduled.forEach { testJobSupplier.unschedule(jobScheduler, it) }
    }

    private class CountingJob(
        id: String = uid("j-"),
        private val duration: Duration = Duration.ZERO,
        private val timeout: Duration? = null,
    ) : Job {
        val runs = AtomicInteger()
        private val key = JobCategory.of("test").withName("Test").getType("quartz").withName("Quartz").getKey(id)
        override fun getKey(): JobKey = key
        override fun getTask() = JobRun {
            runs.incrementAndGet()
            if (!duration.isZero) {
                Thread.sleep(duration.toMillis())
            }
        }

        override fun getDescription(): String = "Counting job ${key.id}"
        override fun isDisabled(): Boolean = false
        override fun getTimeout(): Duration? = timeout
    }

    private fun schedule(job: Job, schedule: Schedule = Schedule.NONE): Job {
        testJobSupplier.schedule(jobScheduler, job, schedule)
        scheduled += job.key
        return job
    }

    private fun status(job: Job): JobStatus =
        jobScheduler.getJobStatus(job.key).getOrNull() ?: error("No status for ${job.key}")

    private fun history(job: Job) =
        jobHistoryService.getHistory(job.key, Time.now.minusHours(1), Time.now.plusHours(1), skipErrors = false)

    @Test
    fun `A supplied job is scheduled, with an ID which identifies it`() {
        val job = schedule(CountingJob())
        val id = status(job).id
        assertEquals(job.key, jobScheduler.getJobKey(id).getOrNull(), "The ID identifies the job")
        assertTrue(job.key in jobScheduler.allJobKeys, "The job is listed")
        assertEquals("Counting job ${job.key.id}", status(job).description)
    }

    @Test
    fun `The ID of a job does not change while the job exists`() {
        val job = schedule(CountingJob(), Schedule.everyMinutes(60).after(Duration.ofMinutes(60)))
        val id = status(job).id
        jobScheduler.refresh()
        assertEquals(id, status(job).id, "Same ID after a refresh")
        // New schedule
        schedule(job, Schedule.everyMinutes(30).after(Duration.ofMinutes(60)))
        assertEquals(id, status(job).id, "Same ID after a change of schedule")
        assertEquals(Schedule.everyMinutes(30).after(Duration.ofMinutes(60)), status(job).schedule)
    }

    @Test
    fun `A job no longer supplied is removed`() {
        val job = schedule(CountingJob())
        val id = status(job).id
        testJobSupplier.unschedule(jobScheduler, job.key)
        assertNull(jobScheduler.getJobStatus(job.key).getOrNull(), "No more job")
        assertNull(jobScheduler.getJobKey(id).getOrNull(), "No more ID")
    }

    @Test
    fun `A job run now runs once, and its run is recorded`() {
        val job = schedule(CountingJob()) as CountingJob
        val run = jobScheduler.fireImmediately(job.key).getOrNull()
        assertNotNull(run, "Job fired").get(30, TimeUnit.SECONDS)
        assertEquals(1, job.runs.get(), "One run")
        assertEquals(1, history(job).size, "One run in the history")
        assertEquals(1, status(job).runCount)
    }

    @Test
    fun `A job not supplied cannot be run`() {
        val job = CountingJob()
        assertFailsWith<JobNotScheduledException> {
            jobScheduler.fireImmediately(job.key)
        }
    }

    @Test
    fun `A periodic job runs at a fixed rate`() {
        val job = schedule(CountingJob(), Schedule(0, 500, TimeUnit.MILLISECONDS)) as CountingJob
        waitUntil("The periodic job runs several times", interval = 100.milliseconds, timeout = 30.seconds) {
            job.runs.get() >= 3
        }
        assertNotNull(status(job).nextRunDate, "The next run is known")
    }

    @Test
    fun `A cron job runs as per its cron expression`() {
        val cron = "0 0 3 * * *"
        val job = schedule(CountingJob(), Schedule.cron(cron))
        val next = status(job).nextRunDate
        // In the time zone of the JVM, as Quartz
        val expected = CronExpression.parse(cron).next(ZonedDateTime.now())
            ?.withZoneSameInstant(ZoneOffset.UTC)?.toLocalDateTime()
        assertEquals(expected, next)
    }

    @Test
    fun `The pause of a job is stored, and survives a refresh`() {
        val job = schedule(CountingJob(), Schedule(0, 500, TimeUnit.MILLISECONDS)) as CountingJob
        assertTrue(jobScheduler.pause(job.key))
        jobScheduler.refresh()
        assertTrue(status(job).isPaused, "Paused after a refresh")
        assertNoRun(job)
        assertTrue(jobScheduler.resume(job.key))
        assertFalse(status(job).isPaused, "Resumed")
        waitUntil("The resumed job runs", interval = 100.milliseconds, timeout = 30.seconds) {
            job.runs.get() > 0
        }
    }

    @Test
    fun `A job keeps its pause when its schedule changes`() {
        val job = schedule(CountingJob(), Schedule(0, 500, TimeUnit.MILLISECONDS)) as CountingJob
        jobScheduler.pause(job.key)
        schedule(job, Schedule(0, 400, TimeUnit.MILLISECONDS))
        assertTrue(status(job).isPaused, "Still paused")
        assertNoRun(job)
    }

    @Test
    fun `All the jobs paused, none runs, and the jobs paused on their own stay paused when all are resumed`() {
        val running = schedule(CountingJob(), Schedule(0, 500, TimeUnit.MILLISECONDS)) as CountingJob
        val paused = schedule(CountingJob(), Schedule(0, 500, TimeUnit.MILLISECONDS)) as CountingJob
        jobScheduler.pause(paused.key)
        jobScheduler.pause()
        assertTrue(jobScheduler.isPaused, "All the jobs are paused")
        assertNoRun(running)
        jobScheduler.resume()
        assertFalse(jobScheduler.isPaused, "All the jobs are resumed")
        waitUntil("The job runs again", interval = 100.milliseconds, timeout = 30.seconds) {
            running.runs.get() > 0
        }
        assertTrue(status(paused).isPaused, "The job paused on its own is still paused")
        assertEquals(0, paused.runs.get(), "The job paused on its own does not run")
    }

    @Test
    fun `A job created while all the jobs are paused is paused with them`() {
        jobScheduler.pause()
        val job = schedule(CountingJob(), Schedule(0, 500, TimeUnit.MILLISECONDS)) as CountingJob
        assertNoRun(job)
        jobScheduler.resume()
        waitUntil("The job runs once all are resumed", interval = 100.milliseconds, timeout = 30.seconds) {
            job.runs.get() > 0
        }
    }

    @Test
    fun `A job run now runs even when paused`() {
        val job = schedule(CountingJob()) as CountingJob
        jobScheduler.pause(job.key)
        jobScheduler.pause()
        jobScheduler.fireImmediately(job.key).getOrNull()?.get(30, TimeUnit.SECONDS)
        assertEquals(1, job.runs.get(), "Run once")
    }

    @Test
    fun `A delayed fire of a job runs once, a second one being refused while the first one is pending`() {
        val job = schedule(CountingJob()) as CountingJob
        val first = jobScheduler.fireLater(job.key, Duration.ofSeconds(2)).getOrNull()
        assertNotNull(first, "First delayed fire")
        assertNull(jobScheduler.fireLater(job.key, Duration.ofSeconds(2)).getOrNull(), "No second delayed fire")
        assertEquals(0, job.runs.get(), "Not run before the delay")
        first.get(30, TimeUnit.SECONDS)
        assertEquals(1, job.runs.get(), "Run once")
        // Fired again once the fire is gone from Quartz, just after the run
        var again: CompletableFuture<*>? = null
        waitUntil("Fired again once run", interval = 100.milliseconds, timeout = 10.seconds) {
            again = jobScheduler.fireLater(job.key, Duration.ZERO).getOrNull()
            again != null
        }
        again?.get(30, TimeUnit.SECONDS)
        assertEquals(2, job.runs.get(), "Run again")
    }

    @Test
    fun `A job running past its timeout is stopped`() {
        val job = schedule(CountingJob(duration = Duration.ofSeconds(10), timeout = Duration.ofMillis(200))) as CountingJob
        val run = jobScheduler.fireImmediately(job.key).getOrNull()
        assertNotNull(run, "Job fired")
        waitUntil("The job runs", interval = 100.milliseconds, timeout = 30.seconds) {
            status(job).isRunning
        }
        Thread.sleep(300)
        assertEquals(1, jobScheduler.checkForTimeouts(), "One job stopped")
        run.get(5, TimeUnit.SECONDS)
        with(status(job)) {
            assertFalse(isRunning, "No longer running")
            assertEquals(1, lastTimeoutCount, "One timeout")
            assertTrue(isTimeout, "Marked as in timeout")
        }
    }

    @Test
    fun `A running job is stopped`() {
        val job = schedule(CountingJob(duration = Duration.ofSeconds(10))) as CountingJob
        val run = jobScheduler.fireImmediately(job.key).getOrNull()
        assertNotNull(run, "Job fired")
        waitUntil("The job runs", interval = 100.milliseconds, timeout = 30.seconds) {
            status(job).isRunning
        }
        assertTrue(jobScheduler.stop(job.key), "Stopped")
        run.get(5, TimeUnit.SECONDS)
        assertFalse(status(job).isRunning, "No longer running")
        assertEquals(1, history(job).size, "The stopped run is in the history")
    }

    @Test
    fun `A job paused by Yontrack 5 starts paused`() {
        val job = CountingJob()
        settingsRepository.setBoolean(JobListener::class.java, job.key.toString(), true)
        schedule(job, Schedule.everyMinutes(60))
        assertTrue(status(job).isPaused, "Paused as in the settings")
    }

    @Test
    fun `A deleted job is removed from the cluster`() {
        val job = schedule(CountingJob())
        assertTrue(jobScheduler.unschedule(job.key), "Deleted")
        assertNull(jobScheduler.getJobStatus(job.key).getOrNull(), "Gone")
        // Still supplied, back at the next refresh
        jobScheduler.refresh()
        assertNotNull(jobScheduler.getJobStatus(job.key).getOrNull(), "Back")
    }

    /**
     * Once any run in progress is over, no run for a few periods.
     */
    private fun assertNoRun(job: CountingJob) {
        Thread.sleep(500)
        val runs = job.runs.get()
        Thread.sleep(1_500)
        assertEquals(runs, job.runs.get(), "No run while paused")
    }
}
