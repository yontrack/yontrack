package net.nemerosa.ontrack.extension.av.metrics

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.common.api.APIName
import net.nemerosa.ontrack.common.doc.MetricsDocumentation
import net.nemerosa.ontrack.common.doc.MetricsMeterDocumentation
import net.nemerosa.ontrack.common.doc.MetricsMeterTag
import net.nemerosa.ontrack.common.doc.MetricsMeterType
import net.nemerosa.ontrack.model.docs.DocumentationIgnore

@Suppress("ConstPropertyName")
@MetricsDocumentation
@APIName("Auto-versioning metrics")
@APIDescription("Metrics related to the auto-versioning processes.")
object AutoVersioningMetrics {

    private val queueTag = MetricsMeterTag(
        name = Tags.QUEUE,
        description = "Queue name"
    )

    object States {

        @APIDescription("Number of audit entries in a given state.")
        @MetricsMeterDocumentation(
            type = MetricsMeterType.COUNT,
            tags = [
                MetricsMeterTag("state", "State of the audit entry."),
            ]
        )
        const val stateCount = "ontrack_extension_auto_versioning_audit_state"
    }

    object Scheduling {
        @APIDescription("Number of auto-versioning requests being actually scheduled")
        @MetricsMeterDocumentation(
            type = MetricsMeterType.COUNT
        )
        const val scheduledCount = "ontrack_extension_auto_versioning_scheduling_scheduled_count"

        @APIDescription("Number of auto-versioning requests being scheduled but being cancelled before being queued")
        @MetricsMeterDocumentation(
            type = MetricsMeterType.COUNT
        )
        const val schedulingCancelledCount = "ontrack_extension_auto_versioning_scheduling_scheduling_cancelled_count"
    }

    object Queue {

        @APIDescription("Number of auto-versioning requests being actually queued")
        @MetricsMeterDocumentation(
            type = MetricsMeterType.COUNT,
            tags = [
                MetricsMeterTag(
                    name = Tags.ROUTING_KEY,
                    description = "Routing key"
                )
            ]
        )
        const val producedCount = "ontrack_extension_auto_versioning_queue_produced_count"

        @APIDescription("Number of auto-versioning requests being actually consumer after having been queued")
        @MetricsMeterDocumentation(
            type = MetricsMeterType.COUNT,
            tags = [
                MetricsMeterTag(
                    name = Tags.QUEUE,
                    description = "Queue name"
                )
            ]
        )
        const val consumedCount = "ontrack_extension_auto_versioning_queue_consumed_count"
    }

    object Processing {

        @APIDescription("Number of auto-versioning requests whose processing is completed")
        @MetricsMeterDocumentation(
            type = MetricsMeterType.COUNT,
            tags = [
                MetricsMeterTag(
                    name = Tags.OUTCOME,
                    description = "Outcome of the processing"
                )
            ]
        )
        const val completedCount = "ontrack_extension_auto_versioning_processing_completed_count"

        @APIDescription("Number of auto-versioning requests whose processing is errored")
        @MetricsMeterDocumentation(
            type = MetricsMeterType.COUNT,
        )
        const val errorCount = "ontrack_extension_auto_versioning_processing_error_count"

        @APIDescription("Time it has taken to process an auto-versioning request, in milliseconds.")
        @MetricsMeterDocumentation(
            type = MetricsMeterType.TIMER,
            tags = [
                MetricsMeterTag(
                    name = Tags.QUEUE,
                    description = "Queue name"
                ),
                MetricsMeterTag(
                    name = "sourceProject",
                    description = "Name of the source project"
                ),
                MetricsMeterTag(
                    name = "targetProject",
                    description = "Name of the target project"
                ),
                MetricsMeterTag(
                    name = "targetBranch",
                    description = "Name of the target branch"
                ),
            ]
        )
        const val time = "ontrack_extension_auto_versioning_processing_time"
    }

    object PostProcessing {

        @APIDescription("Number of post-processing processes having started")
        @MetricsMeterDocumentation(
            type = MetricsMeterType.COUNT,
            tags = [
                MetricsMeterTag(
                    name = Tags.POST_PROCESSING,
                    description = "Post-processing ID"
                ),
            ]
        )
        const val startedCount = "ontrack_extension_auto_versioning_post_processing_started_count"

        @APIDescription("Number of post-processing processes having successfully completed")
        @MetricsMeterDocumentation(
            type = MetricsMeterType.COUNT,
            tags = [
                MetricsMeterTag(
                    name = Tags.POST_PROCESSING,
                    description = "Post-processing ID"
                ),
            ]
        )
        const val successCount = "ontrack_extension_auto_versioning_post_processing_success_count"

        @APIDescription("Number of post-processing processes having errored")
        @MetricsMeterDocumentation(
            type = MetricsMeterType.COUNT,
            tags = [
                MetricsMeterTag(
                    name = Tags.POST_PROCESSING,
                    description = "Post-processing ID"
                ),
            ]
        )
        const val errorCount = "ontrack_extension_auto_versioning_post_processing_error_count"


        @APIDescription("Time it has taken to run some post-processing, in milliseconds.")
        @MetricsMeterDocumentation(
            type = MetricsMeterType.TIMER,
            tags = [
                MetricsMeterTag(
                    name = Tags.POST_PROCESSING,
                    description = "Post-processing ID"
                ),
                MetricsMeterTag(
                    name = "sourceProject",
                    description = "Name of the source project"
                ),
                MetricsMeterTag(
                    name = "targetProject",
                    description = "Name of the target project"
                ),
                MetricsMeterTag(
                    name = "targetBranch",
                    description = "Name of the target branch"
                ),
            ]
        )
        const val time = "ontrack_extension_auto_versioning_post_processing_time"
    }

    object Retry {

        @APIDescription("Number of auto-versioning orders having failed on a transient error and being rescheduled automatically")
        @MetricsMeterDocumentation(
            type = MetricsMeterType.COUNT,
            tags = [
                MetricsMeterTag(
                    name = "sourceProject",
                    description = "Name of the source project"
                ),
                MetricsMeterTag(
                    name = "targetProject",
                    description = "Name of the target project"
                ),
                MetricsMeterTag(
                    name = "targetBranch",
                    description = "Name of the target branch"
                ),
            ]
        )
        const val scheduledCount = "ontrack_extension_auto_versioning_retry_scheduled_count"

        @APIDescription("Number of auto-versioning orders having finally failed after at least one automatic retry")
        @MetricsMeterDocumentation(
            type = MetricsMeterType.COUNT,
            tags = [
                MetricsMeterTag(
                    name = "sourceProject",
                    description = "Name of the source project"
                ),
                MetricsMeterTag(
                    name = "targetProject",
                    description = "Name of the target project"
                ),
                MetricsMeterTag(
                    name = "targetBranch",
                    description = "Name of the target branch"
                ),
            ]
        )
        const val exhaustedCount = "ontrack_extension_auto_versioning_retry_exhausted_count"
    }

    @DocumentationIgnore
    object Tags {
        const val ROUTING_KEY = "routingKey"
        const val QUEUE = "queue"
        const val OUTCOME = "outcome"
        const val POST_PROCESSING = "postProcessing"
    }

}