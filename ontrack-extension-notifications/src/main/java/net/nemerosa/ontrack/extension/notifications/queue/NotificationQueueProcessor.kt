package net.nemerosa.ontrack.extension.notifications.queue

import io.micrometer.core.instrument.MeterRegistry
import net.nemerosa.ontrack.extension.notifications.NotificationsConfigProperties
import net.nemerosa.ontrack.extension.notifications.metrics.NotificationsMetrics
import net.nemerosa.ontrack.extension.notifications.model.Notification
import net.nemerosa.ontrack.extension.notifications.processing.NotificationProcessingService
import net.nemerosa.ontrack.extension.queue.QueueAckMode
import net.nemerosa.ontrack.extension.queue.QueueMetadata
import net.nemerosa.ontrack.extension.queue.QueueProcessor
import net.nemerosa.ontrack.model.events.SerializableEventService
import net.nemerosa.ontrack.model.metrics.increment
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

@Component
class NotificationQueueProcessor(
    private val serializableEventService: SerializableEventService,
    private val meterRegistry: MeterRegistry,
    private val notificationProcessingService: NotificationProcessingService,
    notificationsConfigProperties: NotificationsConfigProperties,
) : QueueProcessor<NotificationQueuePayload> {
    override val id: String = "notification.processing"
    override val payloadType: KClass<NotificationQueuePayload> = NotificationQueuePayload::class

    override fun isCancelled(payload: NotificationQueuePayload): String? = null

    override val defaultScale: Int? = notificationsConfigProperties.processing.queue.processingQueues

    override fun process(payload: NotificationQueuePayload, queueMetadata: QueueMetadata?) {
        // Extracts the notification from the payload
        val notification = Notification(
            source = payload.source,
            channel = payload.channel,
            channelConfig = payload.channelConfig,
            event = serializableEventService.hydrate(payload.serializableEvent),
            template = payload.template,
        )
        meterRegistry.increment(
            NotificationsMetrics.event_dispatching_dequeued,
            "event" to notification.event.eventType.id,
            "channel" to notification.channel,
        )
        notificationProcessingService.process(notification, emptyMap()) { _, _ -> }
    }

    override fun getRoutingIdentifier(payload: NotificationQueuePayload): String = payload.id

    /**
     * Acks the messages once processed, so that a message in hand when the consumer stops is
     * redelivered. A notification may then be sent twice, when the consumer stops between its
     * sending and the recording of its end.
     */
    override val ackMode: QueueAckMode = QueueAckMode.END
}