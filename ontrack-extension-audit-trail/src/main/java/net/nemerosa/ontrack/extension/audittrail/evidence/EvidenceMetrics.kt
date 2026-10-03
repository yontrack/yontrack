package net.nemerosa.ontrack.extension.audittrail.evidence

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.MultiGauge
import io.micrometer.core.instrument.Tags
import net.nemerosa.ontrack.extension.audittrail.metrics.AuditTrailMetrics
import net.nemerosa.ontrack.job.*
import net.nemerosa.ontrack.model.support.JobProvider
import org.springframework.stereotype.Component

/**
 * Gauges of the number and size of the evidences per project ([AuditTrailMetrics.evidenceCount],
 * [AuditTrailMetrics.evidenceSize]), refreshed every 15 minutes from the database — never read on
 * every scrape. The gauges of a project which no longer has evidences are removed.
 */
@Component
class EvidenceMetrics(
    meterRegistry: MeterRegistry,
    private val evidenceRepository: EvidenceRepository,
) : JobProvider, Job {

    private val count: MultiGauge = MultiGauge.builder(AuditTrailMetrics.evidenceCount)
        .description("Number of the evidences of a project which are not deleted")
        .register(meterRegistry)

    private val size: MultiGauge = MultiGauge.builder(AuditTrailMetrics.evidenceSize)
        .description("Total size, in bytes, of the evidences of a project which are not deleted")
        .register(meterRegistry)

    /**
     * Reads the number and size of the evidences per project, and sets the gauges.
     *
     * @return Number and size of the evidences per project
     */
    @Synchronized
    fun refresh(): List<EvidenceProjectStats> {
        val stats = evidenceRepository.getProjectStats()
        count.register(stats.map { MultiGauge.Row.of(tags(it), it.count) }, true)
        size.register(stats.map { MultiGauge.Row.of(tags(it), it.size) }, true)
        return stats
    }

    private fun tags(stats: EvidenceProjectStats) = Tags.of(AuditTrailMetrics.Tags.PROJECT, stats.project)

    override fun getStartingJobs(): Collection<JobRegistration> = listOf(
        JobRegistration(this, Schedule.everyMinutes(15))
    )

    override fun getKey(): JobKey = JOB_TYPE.getKey("metrics")

    override fun getDescription(): String = "Metrics of the evidences"

    override fun isDisabled(): Boolean = false

    override fun getTask() = JobRun { listener ->
        val stats = refresh()
        listener.message("Evidence metrics refreshed for ${stats.size} project(s)")
    }

    companion object {
        private val JOB_TYPE: JobType = JobCategory.of("audit-trail").withName("Audit trail")
            .getType("evidence-metrics").withName("Metrics of the evidences")
    }
}
