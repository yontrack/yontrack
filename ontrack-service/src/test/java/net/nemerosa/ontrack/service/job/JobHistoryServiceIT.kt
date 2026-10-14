package net.nemerosa.ontrack.service.job

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.jobs.TestJobSupplier
import net.nemerosa.ontrack.job.Job
import net.nemerosa.ontrack.job.JobKey
import net.nemerosa.ontrack.job.JobRun
import net.nemerosa.ontrack.job.JobScheduler
import net.nemerosa.ontrack.job.Schedule
import net.nemerosa.ontrack.model.job.JobHistoryItem
import net.nemerosa.ontrack.model.job.JobHistoryItemStatus
import net.nemerosa.ontrack.model.job.JobHistoryService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlin.jvm.optionals.getOrNull
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class JobHistoryServiceIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var jobHistoryService: JobHistoryService

    @Autowired
    private lateinit var jobScheduler: JobScheduler

    @Autowired
    private lateinit var testJobSupplier: TestJobSupplier

    @Test
    fun `Creating a successful history item`() {
        val now = Time.now
        val jobKey = JobFixtures.jobKey()
        val itemId = jobHistoryService.record(
            jobKey = jobKey,
            startedAt = now.minusMinutes(10),
            endedAt = now.minusMinutes(8),
            error = null,
        )
        val item = jobHistoryService.findById(itemId) ?: fail("History item not found")
        assertTrue(item.id > 0, "Item ID")
        assertEquals(jobKey.id, item.jobKey)
        assertEquals(jobKey.type.key, item.jobType)
        assertEquals(jobKey.type.category.key, item.jobCategory)
        assertEquals(Duration.ofMinutes(2), item.duration)
        assertEquals(JobHistoryItemStatus.SUCCESS, item.status)
        assertEquals(null, item.message)
    }

    @Test
    fun `A successful history item keeps the message of its run`() {
        val now = Time.now
        val itemId = jobHistoryService.record(
            jobKey = JobFixtures.jobKey(),
            startedAt = now.minusMinutes(10),
            endedAt = now.minusMinutes(8),
            error = null,
            message = "Nothing to do",
        )
        val item = jobHistoryService.findById(itemId) ?: fail("History item not found")
        assertEquals(JobHistoryItemStatus.SUCCESS, item.status)
        assertEquals("Nothing to do", item.message)
    }

    @Test
    fun `A failed history item keeps its error, not the message of its run`() {
        val now = Time.now
        val itemId = jobHistoryService.record(
            jobKey = JobFixtures.jobKey(),
            startedAt = now.minusMinutes(10),
            endedAt = now.minusMinutes(8),
            error = "Boom",
            message = "Processing",
        )
        val item = jobHistoryService.findById(itemId) ?: fail("History item not found")
        assertEquals(JobHistoryItemStatus.ERROR, item.status)
        assertEquals("Boom", item.message)
    }

    /**
     * The run happens on a thread of Quartz, in a transaction of its own
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `A successful job run records its last message in the history`() {
        val item = runOnce { listener ->
            listener.message("Starting")
            listener.message("Processed %d items", 3)
        }
        assertEquals(JobHistoryItemStatus.SUCCESS, item.status)
        assertEquals("Processed 3 items", item.message)
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `A failed job run records its error in the history, not its last message`() {
        val item = runOnce { listener ->
            listener.message("Starting")
            error("Boom")
        }
        assertEquals(JobHistoryItemStatus.ERROR, item.status)
        assertEquals("Boom", item.message)
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `A successful job run without any message records no message in the history`() {
        val item = runOnce { }
        assertEquals(JobHistoryItemStatus.SUCCESS, item.status)
        assertEquals(null, item.message)
    }

    /**
     * Runs a job once with the given task, and returns its run in the history.
     */
    private fun runOnce(task: JobRun): JobHistoryItem {
        val key = JobFixtures.jobKey()
        val job = object : Job {
            override fun getKey(): JobKey = key
            override fun getTask(): JobRun = task
            override fun getDescription(): String = "Job ${key.id}"
            override fun isDisabled(): Boolean = false
        }
        testJobSupplier.schedule(jobScheduler, job, Schedule.NONE)
        try {
            val run = jobScheduler.fireImmediately(key).getOrNull()
            assertNotNull(run, "Job fired").handle { _, _ -> }.get(30, TimeUnit.SECONDS)
            val items = jobHistoryService.getHistory(key, Time.now.minusHours(1), Time.now.plusHours(1), skipErrors = false)
            assertEquals(1, items.size, "One run in the history")
            return items.first()
        } finally {
            testJobSupplier.unschedule(jobScheduler, key)
        }
    }

    @Test
    fun `Getting the history of a single job`() {
        withJobHistory { now, jobKey ->
            val items = jobHistoryService.getHistory(
                jobKey = jobKey,
                from = now.minusDays(14),
                to = now,
                skipErrors = false,
            )
            assertEquals(14, items.size)
            assertEquals(7, items.count { it.status == JobHistoryItemStatus.ERROR })
        }
    }

    @Test
    fun `Getting the history of a single job, skipping the errors`() {
        withJobHistory { now, jobKey ->
            val items = jobHistoryService.getHistory(
                jobKey = jobKey,
                from = now.minusDays(14),
                to = now,
                skipErrors = true,
            )
            assertEquals(7, items.size)
            assertEquals(0, items.count { it.status == JobHistoryItemStatus.ERROR })
        }
    }

    @Test
    fun `Getting the histogram of a single job`() {
        withJobHistory { now, jobKey ->
            val histogram = jobHistoryService.getHistogram(
                jobKey = jobKey,
                from = now.minusDays(21),
                to = now,
                interval = Duration.ofDays(7),
                skipErrors = false,
            )
            val items = histogram.items
            assertEquals(3, items.size)

            val lastPeriod = items.last()
            assertTrue(lastPeriod.avgDurationMs > 0, "Average duration > 0")
            assertTrue(lastPeriod.minDurationMs > 0, "Min duration > 0")
            assertTrue(lastPeriod.maxDurationMs > 0, "Max duration > 0")
            assertTrue(lastPeriod.minDurationMs <= lastPeriod.avgDurationMs, "Min <= Avg")
            assertTrue(lastPeriod.avgDurationMs <= lastPeriod.maxDurationMs, "Avg <= Max")
        }
    }

    private fun withJobHistory(
        block: (
            now: LocalDateTime,
            jobKey: JobKey,
        ) -> Unit,
    ) {
        val now = Time.now
        val jobKey = JobFixtures.jobKey()
        repeat(21) {
            val start = now.minusDays(it.toLong()).plusHours(12)
            // Deterministic durations, always strictly positive, and varying inside each period
            val durationMinutes = (it % 7 + 1).toLong()
            jobHistoryService.record(
                jobKey = jobKey,
                startedAt = start,
                endedAt = start.plusMinutes(durationMinutes),
                error = if (it % 2 == 0) "Error $it" else null,
            )
        }
        block(now, jobKey)
    }

}