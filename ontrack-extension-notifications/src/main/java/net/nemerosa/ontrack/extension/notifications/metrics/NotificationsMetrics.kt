package net.nemerosa.ontrack.extension.notifications.metrics

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.common.api.APIName
import net.nemerosa.ontrack.common.doc.MetricsDocumentation
import net.nemerosa.ontrack.common.doc.MetricsMeterDocumentation
import net.nemerosa.ontrack.common.doc.MetricsMeterTag
import net.nemerosa.ontrack.common.doc.MetricsMeterType

/**
 * Names of the metrics for the notifications.
 */
@Suppress("ConstPropertyName")
@MetricsDocumentation
@APIName("Notification metrics")
@APIDescription("Metrics related to the management of the notifications.")
object NotificationsMetrics {

    /**
     * Prefix for all metric names
     */
    private const val prefix = "ontrack_extension_notifications"

    @APIDescription("Number of notification events received.")
    @MetricsMeterDocumentation(
        type = MetricsMeterType.COUNT,
        tags = [
            MetricsMeterTag("event", "Event type")
        ]
    )
    const val event_listening_received = "${prefix}_event_listening_received"

    @APIDescription("Number of notification events being queued for dispatching.")
    @MetricsMeterDocumentation(
        type = MetricsMeterType.COUNT,
        tags = [
            MetricsMeterTag("event", "Event type")
        ]
    )
    const val event_listening_queued = "${prefix}_event_listening_queued"

    @APIDescription("Number of notification events planned for dispatching being dequeued.")
    @MetricsMeterDocumentation(
        type = MetricsMeterType.COUNT,
        tags = [
            MetricsMeterTag("event", "Event type")
        ]
    )
    const val event_listening_dequeued = "${prefix}_event_listening_dequeued"

    @APIDescription("Number of notification events planned for dispatching being in error.")
    @MetricsMeterDocumentation(
        type = MetricsMeterType.COUNT,
    )
    const val event_listening_dequeued_error = "${prefix}_event_listening_dequeued_error"

    @APIDescription("Number of notification events planned for dispatching being scheduled for dispatching.")
    @MetricsMeterDocumentation(
        type = MetricsMeterType.COUNT,
        tags = [
            MetricsMeterTag("event", "Event type")
        ]
    )
    const val event_listening = "${prefix}_event_listening"

    @APIDescription("Number of notification queued for actual dispatching.")
    @MetricsMeterDocumentation(
        type = MetricsMeterType.COUNT,
        tags = [
            MetricsMeterTag("event", "Event type"),
            MetricsMeterTag("channel", "Notification channel"),
        ]
    )
    const val event_dispatching_queued = "${prefix}_event_dispatching_queued"

    @APIDescription("Number of notification being dequeued for actual dispatching.")
    @MetricsMeterDocumentation(
        type = MetricsMeterType.COUNT,
        tags = [
            MetricsMeterTag("event", "Event type"),
            MetricsMeterTag("channel", "Notification channel"),
        ]
    )
    const val event_dispatching_dequeued = "${prefix}_event_dispatching_dequeued"

    @APIDescription("Number of dispatching having led to a result.")
    @MetricsMeterDocumentation(
        type = MetricsMeterType.COUNT,
        tags = [
            MetricsMeterTag("event", "Event type"),
            MetricsMeterTag("channel", "Notification channel"),
            MetricsMeterTag("result", "Result type"),
        ]
    )
    const val event_dispatching_result = "${prefix}_event_dispatching_result"

    @APIDescription("Number of dispatching processes starting.")
    @MetricsMeterDocumentation(
        type = MetricsMeterType.COUNT,
        tags = [
            MetricsMeterTag("event", "Event type"),
            MetricsMeterTag("channel", "Notification channel"),
            MetricsMeterTag("result", "Result type"),
        ]
    )
    const val event_processing_started = "${prefix}_event_processing_started"

    @APIDescription("Number of dispatching processes starting in the channel.")
    @MetricsMeterDocumentation(
        type = MetricsMeterType.COUNT,
        tags = [
            MetricsMeterTag("event", "Event type"),
            MetricsMeterTag("channel", "Notification channel"),
            MetricsMeterTag("result", "Result type"),
        ]
    )
    const val event_processing_channel_started = "${prefix}_event_processing_channel_started"

    @APIDescription("Number of dispatching processes using an unknown channel.")
    @MetricsMeterDocumentation(
        type = MetricsMeterType.COUNT,
        tags = [
            MetricsMeterTag("event", "Event type"),
            MetricsMeterTag("channel", "Notification channel"),
        ]
    )
    const val event_processing_channel_unknown = "${prefix}_event_processing_channel_unknown"

    @APIDescription("Number of dispatching processes whose channel config is invalid.")
    @MetricsMeterDocumentation(
        type = MetricsMeterType.COUNT,
        tags = [
            MetricsMeterTag("event", "Event type"),
            MetricsMeterTag("channel", "Notification channel"),
        ]
    )
    const val event_processing_channel_invalid = "${prefix}_event_processing_channel_invalid"

    @APIDescription("Number of dispatching processes where the channel starts its publication process.")
    @MetricsMeterDocumentation(
        type = MetricsMeterType.COUNT,
        tags = [
            MetricsMeterTag("event", "Event type"),
            MetricsMeterTag("channel", "Notification channel"),
        ]
    )
    const val event_processing_channel_publishing = "${prefix}_event_processing_channel_publishing"

    @APIDescription("Number of dispatching processes where the channel has led to a result.")
    @MetricsMeterDocumentation(
        type = MetricsMeterType.COUNT,
        tags = [
            MetricsMeterTag("event", "Event type"),
            MetricsMeterTag("channel", "Notification channel"),
            MetricsMeterTag("result", "Result type"),
        ]
    )
    const val event_processing_channel_result = "${prefix}_event_processing_channel_result"

    @APIDescription("Number of dispatching processes where the channel has led to an error.")
    @MetricsMeterDocumentation(
        type = MetricsMeterType.COUNT,
        tags = [
            MetricsMeterTag("event", "Event type"),
            MetricsMeterTag("channel", "Notification channel"),
        ]
    )
    const val event_processing_channel_error = "${prefix}_event_processing_channel_error"

    @APIDescription("Number of notification records which could not be saved. The notification itself is not impacted: only its trace in the notification recordings is lost.")
    @MetricsMeterDocumentation(
        type = MetricsMeterType.COUNT,
        tags = [
            MetricsMeterTag("event", "Event type"),
            MetricsMeterTag("channel", "Notification channel"),
        ]
    )
    const val event_processing_recording_error = "${prefix}_event_processing_recording_error"

}