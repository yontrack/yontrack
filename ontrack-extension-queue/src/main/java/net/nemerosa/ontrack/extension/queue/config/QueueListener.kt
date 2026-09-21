package net.nemerosa.ontrack.extension.queue.config

import com.rabbitmq.client.Channel
import io.micrometer.core.instrument.MeterRegistry
import net.nemerosa.ontrack.extension.queue.*
import net.nemerosa.ontrack.extension.queue.metrics.queueMessageReceived
import net.nemerosa.ontrack.extension.queue.metrics.queueProcessCompleted
import net.nemerosa.ontrack.extension.queue.metrics.queueProcessErrored
import net.nemerosa.ontrack.extension.queue.metrics.queueProcessTime
import net.nemerosa.ontrack.extension.queue.record.QueueRecordService
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.security.AuthenticationStorageService
import net.nemerosa.ontrack.model.security.SecurityService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.AcknowledgeMode
import org.springframework.amqp.core.Message
import org.springframework.amqp.rabbit.annotation.RabbitListenerConfigurer
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerEndpoint
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpoint
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistrar
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener
import org.springframework.stereotype.Component
import java.io.IOException

@Component
class QueueListener(
    private val queueConfigProperties: QueueConfigProperties,
    private val queueProcessors: List<QueueProcessor<*>>,
    private val securityService: SecurityService,
    private val authenticationStorageService: AuthenticationStorageService,
    private val queueRecordService: QueueRecordService,
    private val meterRegistry: MeterRegistry,
) : RabbitListenerConfigurer {

    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    override fun configureRabbitListeners(registrar: RabbitListenerEndpointRegistrar) {
        queueProcessors.forEach { queueProcessor ->
            configureListener(registrar, queueProcessor)
        }
    }

    private fun configureListener(
        registrar: RabbitListenerEndpointRegistrar,
        queueProcessor: QueueProcessor<*>
    ) {
        // Default listeners
        val scale = queueConfigProperties.getQueueProcessorScale(queueProcessor)
        (0 until scale).forEach { index ->
            registrar.registerEndpoint(
                createDefaultListener(queueProcessor, index)
            )
        }
    }

    private fun createDefaultListener(
        queueProcessor: QueueProcessor<*>,
        index: Int,
    ): RabbitListenerEndpoint {
        val queue = "${queueProcessor.queueNamePrefix}.$index"
        return SimpleRabbitListenerEndpoint().configure(queue, queueProcessor)
    }

    private fun SimpleRabbitListenerEndpoint.configure(
        queue: String,
        queueProcessor: QueueProcessor<*>
    ): SimpleRabbitListenerEndpoint {
        setId(queue)
        setQueueNames(queue)
        concurrency = "${queueProcessor.minConcurrency}-${queueProcessor.maxConcurrency}"
        setMessageListener(createMessageListener(queueProcessor))
        if (queueProcessor.ackMode != QueueAckMode.AUTO) {
            setAckMode(AcknowledgeMode.MANUAL)
        }
        return this
    }

    private fun <T : Any> createMessageListener(queueProcessor: QueueProcessor<T>) =
        QPMessageListener(queueProcessor)

    private inner class QPMessageListener<T : Any>(
        private val queueProcessor: QueueProcessor<T>
    ) : ChannelAwareMessageListener {

        override fun onMessage(message: Message, channel: Channel?) {

            if (queueProcessor.ackMode == QueueAckMode.IMMEDIATE) {
                ackMessage(message, channel)
            }

            try {
                val queue: String = message.messageProperties.consumerQueue
                    ?: error("No consumer queue for the message")
                val body = message.body.toString(Charsets.UTF_8).parseAsJson()
                val qp = QueuePayload.parse(body)
                meterRegistry.queueMessageReceived(qp)
                queueRecordService.received(qp, queue)

                // Checks the processor
                if (qp.processor != queueProcessor.id) {
                    throw IllegalStateException("Queue processor ${queueProcessor.id} received message for ${qp.processor}...")
                }

                // Parsing the payload
                val payload = qp.parse(queueProcessor.payloadType)
                logger.debug("Received: {}", payload)
                queueRecordService.parsed(qp, payload)

                // Check for processing
                val cancelReason = queueProcessor.isCancelled(payload)
                if (cancelReason != null) {
                    logger.debug("Cancelled: {}", payload)
                    queueRecordService.cancelled(qp, cancelReason)
                    return
                }

                // Processing
                securityService.asAdmin {
                    queueRecordService.processing(qp)

                    // Account ID
                    val accountId = qp.accountName
                    authenticationStorageService.withAccountId(accountId) {
                        try {
                            meterRegistry.queueProcessTime(qp) {
                                logger.debug("Processing: {}", payload)
                                queueProcessor.process(
                                    payload = payload,
                                    queueMetadata = QueueMetadata(
                                        queueName = queue,
                                    )
                                )
                            }
                            meterRegistry.queueProcessCompleted(qp)
                            queueRecordService.completed(qp)
                        } catch (any: Exception) {
                            meterRegistry.queueProcessErrored(qp)
                            queueRecordService.errored(qp, any)
                            throw any
                        }
                    }
                }
            } catch (any: Throwable) {
                logger.error("Uncaught error during the queue processing (processor = ${queueProcessor.id})", any)
            } finally {
                if (queueProcessor.ackMode == QueueAckMode.END) {
                    ackMessage(message, channel)
                }
            }
        }

        private fun ackMessage(message: Message, channel: Channel?) {
            if (channel != null) {
                // Always acknowledge the message to prevent re-delivery
                try {
                    logger.debug("Ack received message {}", message)
                    channel.basicAck(message.messageProperties.deliveryTag, false)
                } catch (e: IOException) {
                    // Log failing to acknowledge the message
                    logAckError(e)
                }
            }
        }

        private fun logAckError(e: IOException) {
            logger.error(
                "Message could not be acked (processor: ${queueProcessor.id}): ${e.message}", e
            )
        }

    }

}

