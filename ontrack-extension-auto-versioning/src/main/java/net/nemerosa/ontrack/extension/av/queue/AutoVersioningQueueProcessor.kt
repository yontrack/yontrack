package net.nemerosa.ontrack.extension.av.queue

import net.nemerosa.ontrack.extension.av.AutoVersioningConfigProperties
import net.nemerosa.ontrack.extension.av.audit.AutoVersioningAuditQueryService
import net.nemerosa.ontrack.extension.av.audit.AutoVersioningAuditService
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
) : QueueProcessor<AutoVersioningQueuePayload> {

    private val logger: Logger = LoggerFactory.getLogger(AutoVersioningQueueProcessor::class.java)

    override val id: String = "auto-versioning"

    override val payloadType: KClass<AutoVersioningQueuePayload> = AutoVersioningQueuePayload::class

    override val defaultScale: Int? = autoVersioningConfigProperties.queue.scale

    /**
     * Always ack the auto-versioning requests.
     */
    override val ackMode: QueueAckMode = QueueAckMode.IMMEDIATE

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
        logger.info("Processing order [{}] started...", payload.order.uuid)
        val order = payload.order
        val queue = queueMetadata?.queueName
        metrics.onReceiving(order, queue)
        securityService.asAdmin {
            autoVersioningAuditService.onReceived(order, queue ?: "n/a")
            try {
                val outcome = metrics.processingTiming(order, queue) {
                    // Transient failures are retried by the queue
                    autoVersioningProcessingService.process(order, automaticRetries = true)
                }
                logger.info("Processing order [{}] completed.", payload.order.uuid)
                metrics.onProcessingCompleted(order, outcome)
            } catch (any: Throwable) {
                // Records the error, and either schedules an automatic retry or notifies the error
                autoVersioningRetryService.onError(order, any)
                metrics.onProcessingError()
            }
        }
    }

    override fun getRoutingIdentifier(payload: AutoVersioningQueuePayload): String =
        payload.routingIdentifier()

}