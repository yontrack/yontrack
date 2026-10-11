package net.nemerosa.ontrack.job.support

import net.nemerosa.ontrack.job.JobKey
import net.nemerosa.ontrack.job.JobRunLock
import net.nemerosa.ontrack.job.JobRunLockHandle
import net.nemerosa.ontrack.job.Schedule
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.concurrent.ExecutionException
import kotlin.test.*

/**
 * A run of a job is guarded by a [JobRunLock], so that at most one instance of Yontrack runs a
 * given job at a time.
 */
class JobRunLockTest : AbstractJobTest() {

    @Test
    fun `A scheduled run is skipped silently while the lock is held elsewhere, and runs once it is free`() {
        val lock = FakeJobRunLock(heldElsewhere = true)
        val history = FakeJobHistoryRecorder()
        scheduler(jobRunLock = lock, jobHistoryRecorder = history) {
            job {
                tick_seconds(2)
                // Skipped: nothing has run, nothing is recorded, the status has not changed
                assertEquals(0, count, "The job has not run")
                assertTrue(history.records.isEmpty(), "No history is recorded")
                status(key) {
                    assertEquals(0, runCount, "No run is counted")
                    assertNull(lastRunDate, "No last run date")
                    assertFalse(isRunning, "The job is not running")
                    assertEquals(0, lastErrorCount, "No error")
                    assertNull(lastError, "No error")
                }
                assertTrue(lock.attempts.isNotEmpty(), "The lock has been tried")
                assertTrue(lock.attempts.all { it == key }, "The lock is keyed on the job key")
                // The lock is now free: the next tick runs the job
                lock.heldElsewhere = false
                tick_seconds(1)
                assertEquals(1, count, "The job has run on the next tick")
                assertEquals(listOf(key), history.records.map { it.jobKey }, "The run is recorded")
                status(key) {
                    assertEquals(1, runCount, "One run is counted")
                }
                assertEquals(0, lock.held, "The lock is released after the run")
            }
        }
    }

    @Test
    fun `A run now goes through the same lock`() {
        val lock = FakeJobRunLock(heldElsewhere = true)
        val history = FakeJobHistoryRecorder()
        scheduler(jobRunLock = lock, jobHistoryRecorder = history) {
            job(Schedule.NONE) {
                val future = fireImmediately(this).orElseThrow { IllegalStateException("No future") }
                jobPool.runUntilIdle()
                assertTrue(future.isDone, "The skipped run is complete")
                assertFalse(future.isCompletedExceptionally, "A skipped run is no failure")
                assertEquals(0, count, "The job has not run")
                assertTrue(history.records.isEmpty(), "No history is recorded")
                status(key) {
                    assertEquals(0, runCount, "No run is counted")
                    assertFalse(isRunning, "The job is not running")
                }
                // Once the lock is free, the job can be run now
                lock.heldElsewhere = false
                fireImmediatelyRequired(this)
                jobPool.runUntilIdle()
                assertEquals(1, count, "The job has run")
                assertEquals(1, history.records.size, "The run is recorded")
            }
        }
    }

    @Test
    fun `The lock is held during the run and released afterwards`() {
        val lock = FakeJobRunLock()
        scheduler(jobRunLock = lock) {
            val job = schedule(ConfigurableJob(), Schedule.NONE)
            job.onRun = {
                assertEquals(1, lock.held, "The lock is held during the run")
            }
            fireImmediatelyRequired(job)
            jobPool.runUntilIdle()
            assertEquals(1, job.count)
            assertEquals(0, lock.held, "The lock is released")
            assertEquals(1, lock.released)
        }
    }

    @Test
    fun `The lock is released when the job fails`() {
        val lock = FakeJobRunLock()
        val history = FakeJobHistoryRecorder()
        scheduler(jobRunLock = lock, jobHistoryRecorder = history) {
            val job = schedule(ConfigurableJob(fail = true), Schedule.NONE)
            fireImmediatelyRequired(job)
            jobPool.runUntilIdle()
            assertEquals(0, lock.held, "The lock is released")
            assertEquals(1, lock.released)
            assertEquals("Task failure", history.records.single().error, "The failure is recorded")
            status(job.key) {
                assertEquals(1, lastErrorCount)
                assertFalse(isRunning)
            }
        }
    }

    @Test
    fun `A run whose lock cannot be acquired is skipped and the next tick tries again`() {
        val lock = FakeJobRunLock(failure = IllegalStateException("No database"))
        val history = FakeJobHistoryRecorder()
        scheduler(jobRunLock = lock, jobHistoryRecorder = history) {
            job(Schedule.NONE) {
                val future = fireImmediately(this).orElseThrow { IllegalStateException("No future") }
                jobPool.runUntilIdle()
                val ex = assertFailsWith<ExecutionException> { future.get() }
                assertEquals("No database", ex.cause?.message)
                assertEquals(0, count, "The job has not run")
                assertTrue(history.records.isEmpty(), "No history is recorded")
                status(key) {
                    assertFalse(isRunning, "The job is not running")
                }
                // The database is back
                lock.failure = null
                fireImmediatelyRequired(this)
                jobPool.runUntilIdle()
                assertEquals(1, count, "The job has run")
            }
        }
    }

    @Test
    fun `The scheduler runs without a lock by default`() {
        scheduler {
            job {
                tick_seconds(1)
                assertEquals(2, count)
            }
        }
    }

    private class FakeJobRunLock(
        var heldElsewhere: Boolean = false,
        var failure: Exception? = null,
    ) : JobRunLock {

        val attempts = mutableListOf<JobKey>()
        var held = 0
        var released = 0

        override fun tryLock(key: JobKey): JobRunLockHandle? {
            attempts += key
            failure?.let { throw it }
            return if (heldElsewhere) {
                null
            } else {
                held++
                JobRunLockHandle {
                    held--
                    released++
                }
            }
        }
    }

    private class FakeJobHistoryRecorder : JobHistoryRecorder {

        data class Record(val jobKey: JobKey, val error: String?)

        val records = mutableListOf<Record>()

        override fun record(jobKey: JobKey, startedAt: LocalDateTime, endedAt: LocalDateTime, error: String?): Int {
            records += Record(jobKey, error)
            return records.size
        }
    }
}
