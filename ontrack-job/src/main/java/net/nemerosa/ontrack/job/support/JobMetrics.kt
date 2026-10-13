package net.nemerosa.ontrack.job.support

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.common.api.APIName
import net.nemerosa.ontrack.common.doc.MetricsDocumentation
import net.nemerosa.ontrack.common.doc.MetricsMeterDocumentation
import net.nemerosa.ontrack.common.doc.MetricsMeterTag
import net.nemerosa.ontrack.common.doc.MetricsMeterType

@Suppress("ConstPropertyName")
@MetricsDocumentation
@APIName("Background jobs metrics")
@APIDescription("Metrics for the execution of background jobs.")
object JobMetrics {

    @APIDescription("Total number of registered background jobs. In the cluster, read from the database: every replica reports the same value - aggregate it with max, not sum.")
    @MetricsMeterDocumentation(
        type = MetricsMeterType.COUNT
    )
    const val ontrack_job_count_total = "ontrack_job_count_total"

    @APIDescription("Total number of running background jobs. In the cluster, read from the database: every replica reports the same value - aggregate it with max, not sum.")
    @MetricsMeterDocumentation(
        type = MetricsMeterType.COUNT
    )
    const val ontrack_job_running_total = "ontrack_job_running_total"

    @APIDescription("Total number of disabled background jobs. In the cluster, read from the database: every replica reports the same value - aggregate it with max, not sum.")
    @MetricsMeterDocumentation(
        type = MetricsMeterType.COUNT
    )
    const val ontrack_job_disabled_total = "ontrack_job_disabled_total"

    @APIDescription("Total number of paused background jobs. In the cluster, read from the database: every replica reports the same value - aggregate it with max, not sum.")
    @MetricsMeterDocumentation(
        type = MetricsMeterType.COUNT
    )
    const val ontrack_job_paused_total = "ontrack_job_paused_total"

    @APIDescription("Total number of invalid background jobs. In the cluster, read from the database: every replica reports the same value - aggregate it with max, not sum.")
    @MetricsMeterDocumentation(
        type = MetricsMeterType.COUNT
    )
    const val ontrack_job_invalid_total = "ontrack_job_invalid_total"

    @APIDescription("Total number of errored background jobs. In the cluster, read from the database: every replica reports the same value - aggregate it with max, not sum.")
    @MetricsMeterDocumentation(
        type = MetricsMeterType.COUNT
    )
    const val ontrack_job_error_total = "ontrack_job_error_total"

    @APIDescription("Total number of timed out background jobs. In the cluster, read from the database: every replica reports the same value - aggregate it with max, not sum.")
    @MetricsMeterDocumentation(
        type = MetricsMeterType.COUNT
    )
    const val ontrack_job_timeout_total = "ontrack_job_timeout_total"

    @APIDescription("Duration of the runs of a job, on this replica - of every job, whether it runs once per period in the cluster or on every replica")
    @MetricsMeterDocumentation(
        type = MetricsMeterType.TIMER,
    )
    const val ontrack_job_duration = "ontrack_job_duration"

    @APIDescription("Number of the failed runs of a job running on every replica, on this replica. These jobs are not on the jobs page.")
    @MetricsMeterDocumentation(
        type = MetricsMeterType.COUNT,
        tags = [
            MetricsMeterTag(tag_job_category, "Category of the job"),
            MetricsMeterTag(tag_job_type, "Type of the job"),
            MetricsMeterTag(tag_job_key, "ID of the job"),
        ],
    )
    const val ontrack_job_replica_error = "ontrack_job_replica_error"

    const val tag_job_category = "job_category"
    const val tag_job_type = "job_type"
    const val tag_job_key = "job_key"
}