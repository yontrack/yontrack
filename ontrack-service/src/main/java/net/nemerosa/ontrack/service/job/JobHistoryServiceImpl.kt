package net.nemerosa.ontrack.service.job

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.job.JobKey
import net.nemerosa.ontrack.model.job.*
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import net.nemerosa.ontrack.model.settings.JobHistorySettings
import net.nemerosa.ontrack.repository.JobHistoryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDateTime

@Service
@Transactional
class JobHistoryServiceImpl(
    private val jobHistoryRepository: JobHistoryRepository,
    private val cachedSettingsService: CachedSettingsService,
) : JobHistoryService {

    override fun record(
        jobKey: JobKey,
        startedAt: LocalDateTime,
        endedAt: LocalDateTime,
        error: String?,
        message: String?,
    ): Int {
        val failed = !error.isNullOrBlank()
        val status = if (failed) JobHistoryItemStatus.ERROR else JobHistoryItemStatus.SUCCESS
        return jobHistoryRepository.record(
            item = JobHistoryItem(
                id = 0,
                jobCategory = jobKey.type.category.key,
                jobType = jobKey.type.key,
                jobKey = jobKey.id,
                startedAt = startedAt,
                endedAt = endedAt,
                status = status,
                message = if (failed) error else message,
            )
        )
    }

    override fun cleanup() {
        val retention = cachedSettingsService.getCachedSettings(JobHistorySettings::class.java).retention
        val cutoffTime = Time.now.minus(retention)
        jobHistoryRepository.cleanup(cutoffTime)
    }

    override fun findById(itemId: Int): JobHistoryItem? =
        jobHistoryRepository.findById(itemId)

    override fun getHistory(
        jobKey: JobKey,
        from: LocalDateTime,
        to: LocalDateTime,
        skipErrors: Boolean
    ): List<JobHistoryItem> =
        jobHistoryRepository.getHistory(
            jobCategory = jobKey.type.category.key,
            jobType = jobKey.type.key,
            jobKey = jobKey.id,
            from = from,
            to = to,
            skipErrors = skipErrors
        )

    override fun getHistogram(
        jobKey: JobKey,
        from: LocalDateTime,
        to: LocalDateTime,
        interval: Duration,
        skipErrors: Boolean
    ): JobHistogram {
        val items = getHistory(
            jobKey = jobKey,
            from = from,
            to = to,
            skipErrors = skipErrors
        )

        val histogramItems = mutableListOf<JobHistogramItem>()
        var currentPeriodEnd = to

        while (currentPeriodEnd.isAfter(from)) {
            val periodStart = currentPeriodEnd.minus(interval)

            val itemsInPeriod = items.filter { historyItem ->
                historyItem.endedAt.isAfter(periodStart) && !historyItem.endedAt.isAfter(currentPeriodEnd)
            }

            val count = itemsInPeriod.size
            val durations = itemsInPeriod.map { historyItem -> historyItem.duration.toMillis() }
            val averageDurationMs = if (count > 0) {
                durations.average().toLong()
            } else {
                0L
            }
            val minDurationMs = durations.minOrNull() ?: 0L
            val maxDurationMs = durations.maxOrNull() ?: 0L

            histogramItems.add(
                0,
                JobHistogramItem(
                    from = periodStart,
                    to = currentPeriodEnd,
                    count = count,
                    errorCount = itemsInPeriod.count { it.status == JobHistoryItemStatus.ERROR },
                    avgDurationMs = averageDurationMs,
                    minDurationMs = minDurationMs,
                    maxDurationMs = maxDurationMs,
                )
            )

            currentPeriodEnd = periodStart
        }

        return JobHistogram(
            from = from,
            to = to,
            interval = interval,
            items = histogramItems
        )
    }
}