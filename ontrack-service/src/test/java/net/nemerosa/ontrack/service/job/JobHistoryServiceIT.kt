package net.nemerosa.ontrack.service.job

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.job.JobKey
import net.nemerosa.ontrack.model.job.JobHistoryItemStatus
import net.nemerosa.ontrack.model.job.JobHistoryService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class JobHistoryServiceIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var jobHistoryService: JobHistoryService

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