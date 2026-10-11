package net.nemerosa.ontrack.service.job

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.job.*
import net.nemerosa.ontrack.job.support.DefaultJobScheduler
import net.nemerosa.ontrack.job.support.TaskExecutor
import net.nemerosa.ontrack.model.job.JobHistoryItemStatus
import net.nemerosa.ontrack.model.job.JobHistoryService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

/**
 * Two instances of Yontrack, each with its own job scheduler, against the same database.
 */
class JobRunLockIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var jobRunLock: JobRunLock

    @Autowired
    private lateinit var jobHistoryService: JobHistoryService

    private lateinit var executor: ExecutorService

    @BeforeEach
    fun createExecutor() {
        executor = Executors.newCachedThreadPool()
    }

    @AfterEach
    fun shutdownExecutor() {
        executor.shutdownNow()
    }

    private fun instance(): JobScheduler = DefaultJobScheduler(
        jobDecorator = NOPJobDecorator.INSTANCE,
        // The jobs of these tests are never scheduled, only fired
        scheduler = object : TaskExecutor {
            override fun scheduleAtFixedDelay(task: Runnable, initialDelay: Duration, delay: Duration) =
                error("Not scheduled")

            override fun scheduleCron(task: Runnable, cron: String) =
                error("Not scheduled")
        },
        jobListener = NOPJobListener.INSTANCE,
        initiallyPaused = false,
        jobExecutorService = executor,
        scattering = false,
        scatteringRatio = 0.0,
        jobHistoryRecorder = jobHistoryService,
        jobRunLock = jobRunLock,
    )

    private fun history(key: JobKey) = jobHistoryService.getHistory(
        jobKey = key,
        from = Time.now.minusHours(1),
        to = Time.now.plusHours(1),
        skipErrors = false,
    )

    private class TestLockJob(
        private val key: JobKey,
        private val code: () -> Unit,
    ) : Job {
        override fun getKey(): JobKey = key
        override fun getTask(): JobRun = JobRun { code() }
        override fun getDescription(): String = "Test job"
        override fun isDisabled(): Boolean = false
    }

    private fun JobScheduler.fire(key: JobKey): CompletableFuture<*> =
        fireImmediately(key).orElseThrow { IllegalStateException("Job not fired") }

    @Test
    fun `Two instances firing the same job together run it once`() {
        val key = JobFixtures.jobKey()
        val runs = AtomicInteger()
        val release = CountDownLatch(1)
        val job = TestLockJob(key) {
            runs.incrementAndGet()
            // Keeps on running until the other instance has given up
            release.await(30, TimeUnit.SECONDS)
        }
        val instances = listOf(instance(), instance())
        instances.forEach { it.schedule(job, Schedule.NONE) }
        // Firing both instances together
        val start = CountDownLatch(1)
        val firing = instances.map { instance ->
            executor.submit<CompletableFuture<*>> {
                start.await()
                instance.fire(key)
            }
        }
        start.countDown()
        val futures = firing.map { it.get(30, TimeUnit.SECONDS) }
        // One of the instances skips the run while the other one runs the job
        CompletableFuture.anyOf(*futures.toTypedArray()).get(30, TimeUnit.SECONDS)
        release.countDown()
        futures.forEach { it.get(30, TimeUnit.SECONDS) }
        // The job has run once
        assertEquals(1, runs.get(), "The job has run once")
        val items = history(key)
        assertEquals(1, items.size, "One run in the history")
        assertEquals(JobHistoryItemStatus.SUCCESS, items.single().status)
        // One run counted across the instances
        assertEquals(1, instances.sumOf { it.getJobStatus(key).get().runCount })
    }

    @Test
    fun `The lock is released when the job throws`() {
        val key = JobFixtures.jobKey()
        val job = TestLockJob(key) {
            error("Job failure")
        }
        val instance = instance()
        instance.schedule(job, Schedule.NONE)
        val future = instance.fire(key)
        val ex = assertFailsWith<ExecutionException> {
            future.get(30, TimeUnit.SECONDS)
        }
        assertEquals("Job failure", ex.cause?.message)
        // The lock is free
        val handle = assertNotNull(jobRunLock.tryLock(key), "The lock is released")
        handle.release()
        // The failure is recorded
        val items = history(key)
        assertEquals(1, items.size, "One run in the history")
        assertEquals(JobHistoryItemStatus.ERROR, items.single().status)
        assertEquals("Job failure", items.single().message)
    }

    @Test
    fun `A run now is skipped while another instance runs the job`() {
        val key = JobFixtures.jobKey()
        val runs = AtomicInteger()
        val job = TestLockJob(key) {
            runs.incrementAndGet()
        }
        val instance = instance()
        instance.schedule(job, Schedule.NONE)
        // Another instance runs the job
        val elsewhere = assertNotNull(jobRunLock.tryLock(key), "Lock acquired")
        try {
            instance.fire(key).get(30, TimeUnit.SECONDS)
            assertEquals(0, runs.get(), "The job has not run")
            assertTrue(history(key).isEmpty(), "No run in the history")
            assertEquals(0, instance.getJobStatus(key).get().runCount, "No run counted")
        } finally {
            elsewhere.release()
        }
        // The other instance is done
        instance.fire(key).get(30, TimeUnit.SECONDS)
        assertEquals(1, runs.get(), "The job has run")
        assertEquals(1, history(key).size, "One run in the history")
    }

}
