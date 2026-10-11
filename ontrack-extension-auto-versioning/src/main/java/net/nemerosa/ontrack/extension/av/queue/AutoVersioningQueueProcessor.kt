package net.nemerosa.ontrack.extension.av.queue

import net.nemerosa.ontrack.extension.av.AutoVersioningConfigProperties
import net.nemerosa.ontrack.extension.av.audit.AutoVersioningAuditQueryService
import net.nemerosa.ontrack.extension.av.audit.AutoVersioningAuditService
import net.nemerosa.ontrack.extension.av.dispatcher.AutoVersioningOrder
import net.nemerosa.ontrack.extension.av.metrics.AutoVersioningMetricsService
import net.nemerosa.ontrack.extension.av.processing.AutoVersioningProcessingService
import net.nemerosa.ontrack.extension.av.retry.AutoVersioningRetryService
import net.nemerosa.ontrack.extension.queue.QueueAckMode
import net.nemerosa.ontrack.extension.queue.QueueMetadata
import net.nemerosa.ontrack.extension.queue.QueueProcessor
import net.nemerosa.ontrack.model.security.SecurityService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

@Component
class AutoVersioningQueueProcessor(
    autoVersioningConfigProperties: AutoVersioningConfigProperties,
    private val autoVersioningAuditQueryService: AutoVersioningAuditQueryService,
    private val metrics: AutoVersioningMetricsService,
    private val securityService: SecurityService,
    private val autoVersioningAuditService: AutoVersioningAuditService,
    private val autoVersioningProcessingService: AutoVersioningProcessingService,
    private val autoVersioningRetryService: AutoVersioningRetryService,
    private val autoVersioningProcessingLock: AutoVersioningProcessingLock,
) : QueueProcessor<AutoVersioningQueuePayload> {

    private val logger: Logger = LoggerFactory.getLogger(AutoVersioningQueueProcessor::class.java)

    override val id: String = "auto-versioning"

    override val payloadType: KClass<AutoVersioningQueuePayload> = AutoVersioningQueuePayload::class

    override val defaultScale: Int? = autoVersioningConfigProperties.queue.scale

    /**
     * Acks the orders once processed, so that an order in hand when the consumer stops is
     * redelivered.
     */
    override val ackMode: QueueAckMode = QueueAckMode.END

    /**
     * First check, without any lock: the decision to process the order is taken by [process],
     * under the lock on its routing identifier and on its audit entry.
     */
    override fun isCancelled(payload: AutoVersioningQueuePayload): String? {
        val order = payload.order
        val entry = autoVersioningAuditQueryService.findByUUID(order.branch, order.uuid)
        return if (entry == null) {
            val message = "No audit entry found upon receiving the processing order [${order.uuid}]"
            logger.warn(message)
            message
        } else if (!entry.mostRecentState.state.isRunning) {
            val message = "Cancelled order [${order.uuid}], not processing"
            logger.warn(message)
            message
        } else {
            null
        }
    }

    override fun process(payload: AutoVersioningQueuePayload, queueMetadata: QueueMetadata?) {
        val order = payload.order
        val queue = queueMetadata?.queueName
        securityService.asAdmin {
            // Orders for the same target are processed one at a time, even by the consumers of
            // two instances sharing the queue during a rolling upgrade
            autoVersioningProcessingLock.withLock(payload.routingIdentifier()) {
                // The order may have been throttled, or already processed when this is a redelivery
                if (autoVersioningAuditService.onReceived(order, queue ?: "n/a")) {
                    processReceived(order, queue)
                } else {
                    logger.warn("Order [{}] is no longer running, not processing", order.uuid)
                }
            }
        }
    }

    private fun processReceived(order: AutoVersioningOrder, queue: String?) {
        logger.info("Processing order [{}] started...", order.uuid)
        metrics.onReceiving(order, queue)
        try {
            val outcome = metrics.processingTiming(order, queue) {
                // Transient failures are retried by the queue
                autoVersioningProcessingService.process(order, automaticRetries = true)
            }
            logger.info("Processing order [{}] completed.", order.uuid)
            metrics.onProcessingCompleted(order, outcome)
        } catch (any: Throwable) {
            // Records the error, and either schedules an automatic retry or notifies the error
            autoVersioningRetryService.onError(order, any)
            metrics.onProcessingError()
        }
    }

    override fun getRoutingIdentifier(payload: AutoVersioningQueuePayload): String =
        payload.routingIdentifier()

}