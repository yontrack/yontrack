package net.nemerosa.ontrack.service.job

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import net.nemerosa.ontrack.job.*
import net.nemerosa.ontrack.job.support.JobMetrics
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Jobs of scope [JobScope.REPLICA] (#2078), run on a local scheduler of the replica.
 */
class ReplicaJobSchedulerTest {

    private val meterRegistry = SimpleMeterRegistry()

    private val decorated = AtomicInteger()

    private val jobDecorator = JobDecorator { _, task ->
        {
            decorated.incrementAndGet()
            task()
        }
    }

    private val scheduler = ReplicaJobScheduler(jobDecorator, meterRegistry)

    @AfterEach
    fun stop() {
        scheduler.stop()
    }

    private class CountingJob(
        id: String = JobFixtures.jobKey().id,
        private val disabled: Boolean = false,
        private val failing: Boolean = false,
    ) : Job {
        val runs = AtomicInteger()
        private val key = JobCategory.of("test").withName("Test").getType("replica").withName("Replica").getKey(id)
        override fun getKey(): JobKey = key
        override fun getTask() = JobRun {
            runs.incrementAndGet()
            if (failing) {
                error("Failing job")
            }
        }

        override fun getDescription(): String = "Counting job ${key.id}"
        override fun isDisabled(): Boolean = disabled
    }

    private fun replica(job: Job, schedule: Schedule = FAST) = JobRegistration(job, schedule, JobScope.REPLICA)

    private fun waitUntil(message: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) {
                fail(message)
            }
            Thread.sleep(20)
        }
    }

    private fun timer(job: Job): Timer? = meterRegistry.find(JobMetrics.ontrack_job_duration)
        .tag(JobMetrics.tag_job_category, job.key.type.category.key)
        .tag(JobMetrics.tag_job_type, job.key.type.key)
        .tag(JobMetrics.tag_job_key, job.key.id)
        .timer()

    private fun errors(job: Job): Counter? = meterRegistry.find(JobMetrics.ontrack_job_replica_error)
        .tag(JobMetrics.tag_job_category, job.key.type.category.key)
        .tag(JobMetrics.tag_job_type, job.key.type.key)
        .tag(JobMetrics.tag_job_key, job.key.id)
        .counter()

    @Test
    fun `A replica job runs periodically once the scheduler is started, decorated`() {
        val job = CountingJob()
        scheduler.reconcile(listOf(replica(job)))
        Thread.sleep(200)
        assertEquals(0, job.runs.get(), "Not run before the start")
        scheduler.start()
        waitUntil("The job runs periodically") { job.runs.get() >= 3 }
        assertTrue(decorated.get() >= 3, "The runs are decorated")
        assertEquals(setOf(job.key), scheduler.keys)
    }

    @Test
    fun `A run is timed, and no error is counted`() {
        val job = CountingJob()
        scheduler.start()
        scheduler.reconcile(listOf(replica(job)))
        waitUntil("The job runs") { (timer(job)?.count() ?: 0) >= 2 }
        val errors = assertNotNull(errors(job), "Error counter registered")
        assertEquals(0.0, errors.count())
    }

    @Test
    fun `A failing run is counted as an error, and the job keeps running`() {
        val job = CountingJob(failing = true)
        scheduler.start()
        scheduler.reconcile(listOf(replica(job)))
        waitUntil("The errors are counted") { (errors(job)?.count() ?: 0.0) >= 3.0 }
        assertTrue((timer(job)?.count() ?: 0) >= 3, "The failing runs are timed")
    }

    @Test
    fun `A disabled job does not run`() {
        val job = CountingJob(disabled = true)
        val other = CountingJob()
        scheduler.start()
        scheduler.reconcile(listOf(replica(job), replica(other)))
        waitUntil("The other job runs") { other.runs.get() >= 3 }
        assertEquals(0, job.runs.get(), "The disabled job does not run")
    }

    @Test
    fun `A job without a schedule never runs`() {
        val job = CountingJob()
        val other = CountingJob()
        scheduler.start()
        scheduler.reconcile(listOf(replica(job, Schedule.NONE), replica(other)))
        waitUntil("The other job runs") { other.runs.get() >= 3 }
        assertEquals(0, job.runs.get(), "The job without a schedule does not run")
    }

    @Test
    fun `A job no longer registered stops running`() {
        val job = CountingJob()
        scheduler.start()
        scheduler.reconcile(listOf(replica(job)))
        waitUntil("The job runs") { job.runs.get() >= 1 }
        scheduler.reconcile(emptyList())
        assertTrue(scheduler.keys.isEmpty(), "No job scheduled")
        // Leaves a run in progress the time to complete
        Thread.sleep(100)
        val runs = job.runs.get()
        Thread.sleep(300)
        assertEquals(runs, job.runs.get(), "The job no longer runs")
    }

    @Test
    fun `A job whose schedule changes is rescheduled`() {
        val job = CountingJob()
        scheduler.start()
        scheduler.reconcile(listOf(replica(job, Schedule.everyMinutes(60).after(Duration.ofMinutes(60)))))
        Thread.sleep(200)
        assertEquals(0, job.runs.get(), "Not run yet")
        scheduler.reconcile(listOf(replica(job)))
        waitUntil("The job runs on its new schedule") { job.runs.get() >= 2 }
    }

    @Test
    fun `A job with a cron schedule runs`() {
        val job = CountingJob()
        scheduler.start()
        scheduler.reconcile(listOf(replica(job, Schedule.cron("* * * * * *"))))
        waitUntil("The job runs every second") { job.runs.get() >= 1 }
    }

    @Test
    fun `No job runs once the scheduler is stopped`() {
        val job = CountingJob()
        scheduler.start()
        scheduler.reconcile(listOf(replica(job)))
        waitUntil("The job runs") { job.runs.get() >= 1 }
        scheduler.stop()
        Thread.sleep(100)
        val runs = job.runs.get()
        Thread.sleep(300)
        assertEquals(runs, job.runs.get(), "The job no longer runs")
    }

    companion object {
        private val FAST = Schedule(0, 50, TimeUnit.MILLISECONDS)
    }
}
