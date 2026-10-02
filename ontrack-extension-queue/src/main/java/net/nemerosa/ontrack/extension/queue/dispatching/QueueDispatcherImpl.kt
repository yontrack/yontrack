package net.nemerosa.ontrack.extension.queue.dispatching

import io.micrometer.core.instrument.MeterRegistry
import net.nemerosa.ontrack.extension.queue.QueueConfigProperties
import net.nemerosa.ontrack.extension.queue.QueuePayload
import net.nemerosa.ontrack.extension.queue.QueueProcessor
import net.nemerosa.ontrack.extension.queue.metrics.queueMessageSent
import net.nemerosa.ontrack.extension.queue.record.QueueRecordService
import net.nemerosa.ontrack.extension.queue.source.QueueSource
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.format
import net.nemerosa.ontrack.model.security.AuthenticationStorageService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.stereotype.Component

/**
 * Synchronous queue dispatching (no queue).
 *
 * Used for testing.
 */
@Component
class QueueDispatcherImpl(
    private val authenticationStorageService: AuthenticationStorageService,
    private val queueConfigProperties: QueueConfigProperties,
    private val amqpTemplate: AmqpTemplate,
    private val queueRecordService: QueueRecordService,
    private val meterRegistry: MeterRegistry,
) : QueueDispatcher {

    private val logger: Logger = LoggerFactory.getLogger(QueueDispatcherImpl::class.java)

    override fun <T : Any> dispatch(
        queueProcessor: QueueProcessor<T>,
        payload: T,
        source: QueueSource?,
        routingFeedback: (routingKey: String) -> Unit
    ): QueueDispatchResult =
        if (sync(queueProcessor)) {
            if (queueConfigProperties.general.warnIfSync) {
                logger.warn("Processing queuing in synchronous mode.")
            }
            // No metadata in sync mode
            queueProcessor.process(payload, queueMetadata = null)
            QueueDispatchResult(type = QueueDispatchResultType.PROCESSED, id = null)
        } else {
            val accountId = authenticationStorageService.getAccountId()
            val queuePayload = QueuePayload.create(
                processor = queueProcessor,
                accountName = accountId,
                body = payload,
                actor = authenticationStorageService.getActor(),
            )
            queueRecordService.start(queuePayload, source)

            val routingKey = queueConfigProperties.getRoutingKey(
                queueProcessor,
                payload
            )
            queueRecordService.setRouting(queuePayload, routingKey)
            routingFeedback(routingKey)

            val message = queuePayload.asJson().format()
            val topic = "ontrack.queue.${queueProcessor.id}"
            amqpTemplate.convertAndSend(
                topic,
                routingKey,
                message,
            )
            meterRegistry.queueMessageSent(queuePayload)
            queueRecordService.sent(queuePayload)
            QueueDispatchResult(
                type = QueueDispatchResultType.PROCESSING,
                id = queuePayload.id,
                routingKey = routingKey,
            )
        }

    private fun <T : Any> sync(queueProcessor: QueueProcessor<T>): Boolean {
        return if (!queueConfigProperties.general.async) {
            true
        } else {
            val sp = queueConfigProperties.specific[queueProcessor.id]
            if (sp != null && !sp.async) {
                true
            } else {
                queueProcessor.sync
            }
        }
    }

}