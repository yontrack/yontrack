package net.nemerosa.ontrack.job.support

import io.mockk.mockk
import net.nemerosa.ontrack.job.*
import net.nemerosa.ontrack.job.orchestrator.JobOrchestrator
import net.nemerosa.ontrack.job.orchestrator.JobOrchestratorSupplier
import net.nemerosa.ontrack.test.assertNotPresent
import net.nemerosa.ontrack.test.assertPresent
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import kotlin.test.*

class JobSchedulingTest : AbstractJobTest() {

    @Test
    fun schedule() {
        scheduler {
            job {
                tick_seconds(3)
                assertEquals(4, count)
            }
        }
    }

    @Test
    fun `Schedule with wait at startup`() {
        scheduler {
            job(Schedule.EVERY_SECOND.after(1)) {
                tick_seconds(3)
                assertEquals(3, count)
            }
        }
    }

    @Test
    fun scheduler_paused_at_startup() {
        scheduler(initiallyPaused = true) {
            job {
                tick_seconds(3)
                assertEquals(0, count) // Job did not run
                // Resumes the execution and waits
                scheduler.resume()
                tick_seconds(2)
                // The job has run
                assertEquals(2, count)
            }
        }
    }

    @Test
    fun statuses() {
        scheduler {
            val longJob = schedule(
                    ConfigurableJob(name = "long", wait = 5000),
                    Schedule.EVERY_MINUTE
            )
            val shortJob = schedule(
                    ConfigurableJob(name = "short"),
                    Schedule.EVERY_SECOND.after(60)
            )
            // The long job is already running, not the short one
            schedulerPool.runNextPendingCommand()
            val statuses = scheduler.jobStatuses
                    .associateBy { it.key }

            val longStatus = statuses[longJob.key]
            assertNotNull(longStatus) { assertTrue(it.isRunning) }

            val shortStatus = statuses[shortJob.key]
            assertNotNull(shortStatus) { assertFalse(it.isRunning) }
        }

    }

    @Test
    fun removing_a_long_running_job() {
        scheduler {
            job {
                // The job is now running
                schedulerPool.runNextPendingCommand()
                val status = scheduler.getJobStatus(key)
                assertPresent(status) {
                    assertTrue(it.isRunning)
                }
                // Now, removes the job
                unschedule(this)
                // Waits a bit, and checks the job has stopped running
                jobPool.runUntilIdle()
                assertEquals(0, count)
            }
        }
    }

    @Test
    fun job_failures() {
        scheduler {
            val job = schedule(
                    ConfigurableJob(fail = true),
                    Schedule.EVERY_SECOND
            )
            // After some seconds, the job keeps running and has only failed
            tick_seconds(3)
            val status = scheduler.getJobStatus(job.key)
            assertPresent(status) {
                val lastErrorCount = it.lastErrorCount
                assertEquals(4, lastErrorCount)
                assertEquals("Task failure", it.lastError)
                // Now, fixes the job
                job.fail = false
                // Waits a bit, and checks the job is now OK
                tick_seconds(2)
                val newStatus = scheduler.getJobStatus(job.key)
                assertPresent(newStatus) {
                    assertEquals(2, job.count)
                    assertEquals(0, it.lastErrorCount)
                    assertNull(it.lastError)
                }
            }
        }
    }

    @Test
    fun reschedule() {
        scheduler {
            job {
                // Checks it has run
                tick_seconds(2)
                val currentCount = count
                assertEquals(3, currentCount)
                // Then every minute
                schedule(this, Schedule(1, 1, TimeUnit.MINUTES))
                // Checks after three more seconds than the count has not moved
                tick_seconds(3)
                assertEquals(currentCount, count)
            }
        }
    }

    @Test
    fun fire_immediately() {
        scheduler {
            job(Schedule.EVERY_MINUTE.after(10)) {
                // Not fired, even after 10 seconds
                tick_seconds(10)
                assertEquals(0, count)
                // Fires immediately and waits for the result
                fireImmediatelyRequired(this)
                jobPool.runUntilIdle()
                assertEquals(1, count)

            }
        }
    }

    @Test
    fun fire_immediately_in_concurrency() {
        scheduler {
            job {
                // Job is started and scheduled
                schedulerPool.runUntilIdle()
                // ... but nothing has happened yet
                assertEquals(0, count)
                // Checks its status
                val jobStatus = scheduler.getJobStatus(key)
                assertPresent(jobStatus) {
                    assertTrue(it.isRunning)
                }
                // Fires immediately and waits for the result
                assertNotPresent(
                        fireImmediately(this),
                        "Job is not fired because already running"
                )
                // The job is already running, count is still 0
                assertEquals(0, count)
                // Waits until completion
                jobPool.runUntilIdle()
                assertEquals(1, count)
            }
        }
    }

    @Test
    fun `Removing a running job`() {
        scheduler {
            val job = job(Schedule.EVERY_SECOND)
            // After some seconds, the job keeps running
            tick_seconds(3)
            assertEquals(4, job.count)
            // Now, removes the job
            unschedule(job)
            // Waits a bit, and checks the job has stopped running
            tick_seconds(3)
            assertEquals(4, job.count)
        }
    }

    @Test
    fun `Disabled job cannot be fired`() {
        scheduler {
            val job = ConfigurableJob()
            job.pause()
            // Initially every second
            schedule(job, Schedule.EVERY_SECOND)
            tick_seconds(2)
            // After a few seconds, the count has NOT moved
            assertEquals(0, job.count)
            // Forcing the run
            val future = fireImmediately(job)
            assertNotPresent(future, "Job not fired")
            // ... to not avail
            assertEquals(0, job.count)
        }
    }

    @Test
    fun job_pause() {
        test_with_pause(
                { _, job -> job.pause() },
                { _, job -> job.resume() }
        )
    }

    @Test
    fun job_schedule_pause() {
        test_with_pause(
                { jobScheduler, job -> jobScheduler.pause(job.key) },
                { jobScheduler, job -> jobScheduler.resume(job.key) }
        )
    }

    @Test
    fun scheduler_pause() {
        test_with_pause(
                { jobScheduler, _ -> jobScheduler.pause() },
                { jobScheduler, _ -> jobScheduler.resume() }
        )
    }

    @Test
    fun stop() {
        scheduler {
            job {
                // Kicks the scheduling of the job
                schedulerPool.runNextPendingCommand()

                // Checks it's running
                status(key) {
                    assertTrue(isRunning, "Job is running")
                }

                // Stops the job
                println("Stopping the job.")
                assertTrue(scheduler.stop(key), "Job has been stopped")

                // Checks it has actually been stopped
                status(key) {
                    assertFalse(isRunning, "Job is actually stopped")
                }
            }
        }
    }

    @Test
    fun keys() {
        scheduler {
            val longCountJob = ConfigurableJob("long")
            val countJob = ConfigurableJob("short")
            val otherTypeJob = ConfigurableJob("other", category = Fixtures.TEST_OTHER_CATEGORY)

            schedule(longCountJob)
            schedule(countJob)
            schedule(otherTypeJob)

            assertEquals(
                    setOf(longCountJob.key, countJob.key, otherTypeJob.key),
                    scheduler.allJobKeys
            )

            assertEquals(
                    setOf(longCountJob.key, countJob.key),
                    scheduler.getJobKeysOfCategory(Fixtures.TEST_CATEGORY)
            )

            assertEquals(
                    setOf(otherTypeJob.key),
                    scheduler.getJobKeysOfCategory(Fixtures.TEST_OTHER_CATEGORY)
            )

        }
    }

    @Test
    fun pause_for_not_schedule_job() {
        assertFailsWith<JobNotScheduledException> {
            scheduler {
                scheduler.pause(JobCategory.of("test").getType("test").getKey("x"))
            }
        }
    }

    @Test
    fun resume_for_not_schedule_job() {
        assertFailsWith<JobNotScheduledException> {
            scheduler {
                scheduler.resume(JobCategory.of("test").getType("test").getKey("x"))
            }
        }
    }

    @Test
    fun fire_immediately_for_not_schedule_job() {
        assertFailsWith<JobNotScheduledException> {
            scheduler {
                scheduler.fireImmediately(JobCategory.of("test").getType("test").getKey("x"))
            }
        }
    }

    @Test
    fun job_status_for_not_schedule_job() {
        scheduler {
            assertNotPresent(scheduler.getJobStatus(JobCategory.of("test").getType("test").getKey("x")))
        }
    }

    @Test
    fun invalid_job() {
        scheduler {
            job {
                // After some seconds, the job keeps running
                tick_seconds(3)
                val currentCount = count
                assertEquals(4, count, "Job ran four times")
                // Invalidates the job
                invalidate()
                // The status indicates the job is no longer valid, but is still there
                status(key) {
                    assertFalse(isValid)
                    assertNull(nextRunDate)
                }
                // After some seconds, the job has not run
                tick_seconds(1)
                jobPool.runUntilIdle()
                assertEquals(currentCount, count)
                // ... and it's gone
                assertNotPresent(scheduler.getJobStatus(key))
            }
        }
    }

    @Test
    fun paused_job_can_be_fired() {
        scheduler {
            job {
                tick_seconds(2)
                // After a few seconds, the count has moved
                val currentCount = count
                assertEquals(3, currentCount)
                // Pauses the job now
                scheduler.pause(key)
                // Not running
                tick_seconds(2)
                assertEquals(currentCount, count)
                // Forcing the run
                fireImmediatelyRequired(this)
                jobPool.runUntilIdle()
                assertTrue(count > currentCount)
            }
        }
    }

    @Test
    fun not_scheduled_job_can_be_fired() {
        scheduler {
            job(Schedule.NONE) {
                tick_seconds(2)
                // After a few seconds, the count has NOT moved
                assertEquals(0, count)
                // Forcing the run
                fireImmediatelyRequired(this)
                jobPool.runUntilIdle()
                assertEquals(1, count)
            }
        }
    }

    @Test
    fun not_scheduled_job_cannot_be_paused() {
        scheduler {
            job(Schedule.NONE) {
                tick_seconds(2)
                // After a few seconds, the count has NOT moved
                assertEquals(0, count)
                // Pausing the job
                scheduler.pause(key)
                // Not paused
                status(key) {
                    assertFalse(isPaused, "Job cannot be paused since it is not scheduled.")
                }
            }
        }
    }

    @Test
    fun invalid_job_cannot_be_fired() {
        scheduler {
            val job = ConfigurableJob()
            job.invalidate()
            // Schedules, but not now
            scheduler.schedule(job, Schedule.EVERY_MINUTE.after(1))
            // Forcing the run
            assertNotPresent(fireImmediately(job))
            // ... to not avail
            assertEquals(0, job.count)
            // ... and it's now gone
            assertNotPresent(scheduler.getJobStatus(job.key))
        }
    }

    @Test
    fun scheduler_paused_at_startup_with_orchestration() {
        scheduler(initiallyPaused = true) {
            val job = ConfigurableJob()
            // Job orchestration
            val jobOrchestrator = JobOrchestrator(
                    scheduler,
                    "Orchestrator",
                    listOf(
                            object : JobOrchestratorSupplier {
                                override val jobRegistrations: Collection<JobRegistration> = listOf(
                                        JobRegistration.of(job).withSchedule(Schedule.EVERY_SECOND)
                                )
                            }

                    ),
                    mockk(relaxed = true)
            )
            // Registers the orchestrator
            schedule(jobOrchestrator, Schedule.EVERY_SECOND)
            // Waits some time...
            tick_seconds(3)
            // ... and the job should not have run
            assertEquals(0, job.count)
            // ... and the orchestrator must not have run
            status(jobOrchestrator.key) {
                assertEquals(0, runCount)
                assertFalse(isRunning)
            }
            // Resumes the job scheduler
            scheduler.resume()
            // Resumes all jobs
            schedulerPool.runUntilIdle()
            // Waits for one second for the orchestrator to kick off
            tick_seconds(1)
            // Forces the registration of pending jobs
            schedulerPool.runUntilIdle()
            jobPool.runUntilIdle()
            // The job managed by the orchestrator must have run
            assertEquals(1, job.count)
        }
    }

    @Test
    fun `Updating the schedule of a job`() {
        scheduler {
            job {
                // Checks it has run
                tick_seconds(2)
                val currentCount = count
                assertEquals(3, currentCount)
                // Then every minute
                schedule(this, Schedule(1, 1, TimeUnit.MINUTES))
                // Checks after three more seconds than the count has not moved
                tick_seconds(3)
                assertEquals(currentCount, count)
                // Checks the new schedule
                status(key) {
                    assertEquals(1, schedule.initialPeriod)
                    assertEquals(1, schedule.period)
                    assertEquals(TimeUnit.MINUTES, schedule.unit)
                }
            }
        }
    }

    @Test
    fun `Updating the description of a job`() {
        scheduler {
            val job = schedule(
                    ConfigurableJob(theDescription = "Description 1")
            )
            // Checks the description status
            status(job.key) {
                assertEquals("Description 1", description)
            }
            // Schedules with a new description
            schedule(ConfigurableJob(theDescription = "Description 2"))
            // Checks the description status
            status(job.key) {
                assertEquals("Description 2", description)
            }
        }
    }

    @Test
    fun `Updating the task of a job`() {
        scheduler {
            job {
                // Checks it has run
                tick_seconds(2)
                assertEquals(3, count)
                // Then with an increment of 10, and the same schedule
                val newJob = schedule(ConfigurableJob(increment = 10))
                // Checks after three more seconds has moved way more
                tick_seconds(3)
                assertEquals(30, newJob.count)
            }
        }
    }

    private fun test_with_pause(
            pause: (JobScheduler, ConfigurableJob) -> Unit,
            resume: (JobScheduler, ConfigurableJob) -> Unit
    ) {
        scheduler {
            job {
                // Runs a few times
                repeat(2) {
                    schedulerPool.tick(1, TimeUnit.SECONDS)
                    jobPool.tick(1, TimeUnit.SECONDS)
                }
                // Checks the job has run
                assertEquals(2, count)
                // Pauses
                pause(scheduler, this)
                // After some seconds, the job has not run
                repeat(2) {
                    schedulerPool.tick(1, TimeUnit.SECONDS)
                    jobPool.tick(1, TimeUnit.SECONDS)
                }
                assertEquals(2, count)
                // Resumes the job
                resume(scheduler, this)
                // After some seconds, the job has started again
                repeat(2) {
                    schedulerPool.tick(1, TimeUnit.SECONDS)
                    jobPool.tick(1, TimeUnit.SECONDS)
                }
                assertEquals(4, count)
            }
        }
    }


}