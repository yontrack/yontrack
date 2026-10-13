package net.nemerosa.ontrack.service.job

import io.micrometer.core.instrument.MeterRegistry
import net.nemerosa.ontrack.job.JobKey
import net.nemerosa.ontrack.job.JobListener
import net.nemerosa.ontrack.job.JobRunProgress
import net.nemerosa.ontrack.job.JobStatus
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

open class DefaultJobListener(
    private val meterRegistry: MeterRegistry,
) : JobListener {

    private val logger: Logger = LoggerFactory.getLogger(DefaultJobListener::class.java)

    override fun onJobStart(key: JobKey) {
    }

    override fun onJobEnd(key: JobKey, milliseconds: Long) {
        meterRegistry.timer("ontrack_job_duration_ms", key.metricTags).record(milliseconds, TimeUnit.MILLISECONDS)
        meterRegistry.counter("ontrack_job_run_count", key.metricTags).increment()
    }

    override fun onJobError(status: JobStatus, ex: Exception) {
        val key = status.key
        meterRegistry.counter("ontrack_job_errors", key.metricTags).increment()
        logger.error(
            "${status.description}, key=${key.id}, progress=${status.progressText}",
            ex
        )
    }

    override fun onJobComplete(key: JobKey) {
    }

    override fun onJobProgress(key: JobKey, progress: JobRunProgress) {}
}
