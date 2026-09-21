package net.nemerosa.ontrack.job.orchestrator

import io.mockk.mockk
import net.nemerosa.ontrack.job.*
import net.nemerosa.ontrack.job.orchestrator.TestJob.Companion.getKey
import net.nemerosa.ontrack.job.support.DefaultJobScheduler
import net.nemerosa.ontrack.job.support.TaskExecutor
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.function.Supplier
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JobOrchestratorTest {

    private lateinit var taskExecutor: TaskExecutor
    private lateinit var scheduledExecutorService: ScheduledExecutorService

    @BeforeEach
    fun before() {
        scheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
        taskExecutor = mockk()
    }

    @AfterEach
    fun after() {
        scheduledExecutorService.shutdownNow()
    }

    private fun createJobScheduler(): JobScheduler = DefaultJobScheduler(
            jobDecorator = NOPJobDecorator.INSTANCE,
            scheduler = taskExecutor,
            jobExecutorService = scheduledExecutorService,
            jobListener = OutputJobListener { x: String? -> println(x) },
            initiallyPaused = false,
            scattering = false,
            scatteringRatio = 1.0
    )

    @Test
    fun orchestrator_initial_jobs() {
        val scheduler = createJobScheduler()
        val notScheduledException = Supplier { RuntimeException("Not scheduled") }
        val jobs = mutableListOf<JobRegistration>()
        val jobOrchestrationSupplier = object : JobOrchestratorSupplier {
            override val jobRegistrations: Collection<JobRegistration> = jobs
        }
        val orchestrator = JobOrchestrator(
                scheduler,
                "Test",
                setOf(jobOrchestrationSupplier),
                mockk(relaxed = true),
        )
        val key = orchestrator.key

        // Orchestration is registered as a job, but does not run since we have a NONE schedule
        scheduler.schedule(orchestrator, Schedule.NONE)
        val status = scheduler.getJobStatus(key).orElse(null)
        assertNotNull(status)
        assertNull(status.nextRunDate)

        // Puts a job in the list
        jobs.add(JobRegistration(TestJob("1"), Schedule.NONE))
        // ... and launches the orchestration
        scheduler.fireImmediately(key).orElseThrow(notScheduledException).get()
        // ... tests the job has been registered
        assertTrue(scheduler.getJobStatus(getKey("1")).isPresent)

        // Puts the second job in the list
        jobs.add(JobRegistration(TestJob("2"), Schedule.NONE))
        // ... and launches the orchestration
        scheduler.fireImmediately(key).orElseThrow(notScheduledException).get()
        // ... tests the jobs are registered
        assertTrue(scheduler.getJobStatus(getKey("1")).isPresent)
        assertTrue(scheduler.getJobStatus(getKey("2")).isPresent)

        // Removes the first job in the list
        jobs.removeAt(0)
        // ... and launches the orchestration
        scheduler.fireImmediately(key).orElseThrow(notScheduledException).get()
        // ... tests the jobs are registered
        assertFalse(scheduler.getJobStatus(getKey("1")).isPresent)
        assertTrue(scheduler.getJobStatus(getKey("2")).isPresent)
    }
}