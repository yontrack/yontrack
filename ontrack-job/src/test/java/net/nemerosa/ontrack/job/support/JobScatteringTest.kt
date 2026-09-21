package net.nemerosa.ontrack.job.support

import io.mockk.mockk
import net.nemerosa.ontrack.job.*
import net.nemerosa.ontrack.job.Schedule.Companion.everyMinutes
import net.nemerosa.ontrack.job.orchestrator.JobOrchestrator
import net.nemerosa.ontrack.job.orchestrator.JobOrchestratorSupplier
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import kotlin.jvm.optionals.getOrNull
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JobScatteringTest : AbstractJobTest() {

    private fun createJobScheduler(
            scattering: Boolean,
            scatteringRatio: Double
    ) = DefaultJobScheduler(
            jobDecorator = NOPJobDecorator.INSTANCE,
            scheduler = scheduler,
            jobListener = NOPJobListener.INSTANCE,
            initiallyPaused = false,
            jobExecutorService = jobPool,
            scattering = scattering,
            scatteringRatio = scatteringRatio
    )

    @Test
    fun scatteredScheduleDisabled() {
        val scheduler = createJobScheduler(
                scattering = false,
                scatteringRatio = 1.0
        )
        val job = ConfigurableJob()
        scheduler.schedule(job, everyMinutes(30))

        // Gets the schedule of the job
        val status = scheduler.getJobStatus(job.key).orElse(null)
        assertNotNull(status)
        assertEquals(0L, status.schedule.initialPeriod)
        assertEquals(30L, status.schedule.period)
        assertEquals(TimeUnit.MINUTES, status.schedule.unit)
        assertEquals(0L, status.actualSchedule.initialPeriod)
        assertEquals(30 * 60 * 1000L, status.actualSchedule.period)
        assertEquals(TimeUnit.MILLISECONDS, status.actualSchedule.unit)
    }

    @Test
    fun scatteredScheduleEnabled() {
        val scheduler = createJobScheduler(
                scattering = true,
                scatteringRatio = 0.5
        )
        val job = ConfigurableJob()
        scheduler.schedule(job, everyMinutes(30))

        // Gets the schedule of the job
        val status = scheduler.getJobStatus(job.key).orElse(null)
        assertNotNull(status)
        assertEquals(0L, status.schedule.initialPeriod)
        assertEquals(30L, status.schedule.period)
        assertEquals(TimeUnit.MINUTES, status.schedule.unit)
        val actualInitialPeriod = status.actualSchedule.initialPeriod
        assertTrue(actualInitialPeriod >= 0L)
        assertTrue(actualInitialPeriod <= 15 * 60 * 1000L)
        assertEquals(30 * 60 * 1000L, status.actualSchedule.period)
        assertEquals(TimeUnit.MILLISECONDS, status.actualSchedule.unit)
    }

    @Test
    fun scatteredScheduleEnabledWithInitialDelay() {
        val scheduler = createJobScheduler(
                scattering = true,
                scatteringRatio = 0.5
        )
        val job = ConfigurableJob()
        scheduler.schedule(job, everyMinutes(30).after(10))

        // Gets the schedule of the job
        val status = scheduler.getJobStatus(job.key).orElse(null)
        assertNotNull(status)
        assertEquals(10L, status.schedule.initialPeriod)
        assertEquals(30L, status.schedule.period)
        assertEquals(TimeUnit.MINUTES, status.schedule.unit)
        val actualInitialPeriod = status.actualSchedule.initialPeriod
        assertTrue(actualInitialPeriod >= 10 * 60 * 1000L)
        assertTrue(actualInitialPeriod <= 40 * 60 * 1000L)
        assertEquals(30 * 60 * 1000L, status.actualSchedule.period)
        assertEquals(TimeUnit.MILLISECONDS, status.actualSchedule.unit)
    }

    @Test
    fun scatteredScheduleEnabledWithNoSchedule() {
        val scheduler = createJobScheduler(
                scattering = true,
                scatteringRatio = 0.5
        )
        val job = ConfigurableJob()
        scheduler.schedule(job, Schedule.NONE)

        // Gets the schedule of the job
        val status = scheduler.getJobStatus(job.key).orElse(null)
        assertNotNull(status)
        assertEquals(0L, status.schedule.initialPeriod)
        assertEquals(0L, status.schedule.period)
        assertEquals(TimeUnit.SECONDS, status.schedule.unit)
        assertEquals(0L, status.actualSchedule.initialPeriod)
        assertEquals(0L, status.actualSchedule.period)
        assertEquals(TimeUnit.MILLISECONDS, status.actualSchedule.unit)
    }

    @Test
    fun scatteringInSameType() {
        // Scheduler
        val scheduler = createJobScheduler(
                scattering = true,
                scatteringRatio = 1.0
        )
        // Creates a list of jobs with a weak key
        val jobs = (1..100).map {
            ConfigurableJob("$it")
        }
        // Orchestration of all those jobs every 6 hours
        val jobOrchestratorSupplier = listOf(
                object : JobOrchestratorSupplier {
                    override val jobRegistrations: Collection<JobRegistration>
                        get() = jobs.map {
                            JobRegistration.of(it).everyMinutes(360L)
                        }
                }
        )
        // Orchestrator
        val orchestrator = JobOrchestrator(
                scheduler,
                "Orchestrator",
                jobOrchestratorSupplier,
                mockk(relaxed = true)
        )
        // Scheduling the orchestrator (manual mode)
        scheduler.schedule(orchestrator, Schedule.NONE)
        // Launching the orchestrator (manually)
        orchestrator.orchestrate(JobRunListener.out())
        // Getting the actual schedules of the jobs
        val actualSchedules = jobs
                .mapNotNull { scheduler.getJobStatus(it.key).getOrNull() }
                .map(JobStatus::actualSchedule)
        val initialPeriods = actualSchedules
                .map(Schedule::initialPeriod)
        initialPeriods.forEach(Consumer { l: Long? -> println("--> $l") })
        // Checks that all jobs have been scheduled
        assertEquals(jobs.size.toLong(), initialPeriods.size.toLong(), "All jobs have been scheduled")
        // Checks that all schedules more or less different
        val stats = DescriptiveStatistics()
        initialPeriods.forEach(Consumer { v: Long -> stats.addValue(v.toDouble()) })
        // Gets the std deviation
        val standardDeviation = stats.standardDeviation
        val max = stats.max
        // Gets this in minutes (this was returned in ms)
        val stdDevMinutes = TimeUnit.MINUTES.convert(standardDeviation.toLong(), TimeUnit.MILLISECONDS).toDouble()
        val maxMinutes = TimeUnit.MINUTES.convert(max.toLong(), TimeUnit.MILLISECONDS).toDouble()
        // It must be >> 0
        assertTrue(stdDevMinutes > 60.0, "Std deviation must be >> 0")
        println("Max = $maxMinutes")
        assertTrue(maxMinutes <= 6 * 60.0, "Max is <= period")
    }
}