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
import java.io.InterruptedIOException
import java.nio.channels.ClosedByInterruptException

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

            // Set when the processing is left for the broker to redeliver
            var requeue = false

            try {
                val queue: String = message.messageProperties.consumerQueue
                    ?: error("No consumer queue for the message")
                val body = message.body.toString(Charsets.UTF_8).parseAsJson()
                val qp = QueuePayload.parse(body)
                meterRegistry.queueMessageReceived(qp)
                if (!queueRecordService.received(qp, queue)) {
                    // Redelivery of a message whose processing is already over
                    logger.info(
                        "Skipping the redelivered message {} (processor = {}): its processing is over",
                        qp.id,
                        queueProcessor.id,
                    )
                    return
                }

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
                    // A failure to restore the account is recorded like a failure of the processing
                    try {
                        authenticationStorageService.withAccountId(accountId, qp.actor) {
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
                        }
                    } catch (any: Exception) {
                        meterRegistry.queueProcessErrored(qp)
                        if (isLeftForRedelivery(any, channel)) {
                            // Typically, the listener was stopped and interrupted the processing:
                            // the record is left as is, for the redelivery to process the message
                            requeue = true
                            logger.warn(
                                "Processing of message ${qp.id} interrupted, left for redelivery (processor = ${queueProcessor.id})",
                                any
                            )
                        } else {
                            queueRecordService.errored(qp, any)
                        }
                        throw any
                    }
                }
            } catch (any: Throwable) {
                // A message left for redelivery was already logged
                if (!requeue) {
                    logger.error("Uncaught error during the queue processing (processor = ${queueProcessor.id})", any)
                }
            } finally {
                if (queueProcessor.ackMode == QueueAckMode.END) {
                    if (requeue) {
                        requeueMessage(message, channel)
                    } else {
                        ackMessage(message, channel)
                    }
                }
            }
        }

        /**
         * A message acked at the end of its processing is left for the broker to redeliver when
         * its processing failed because it was interrupted, which is how a stopping listener
         * container ends the processing still running after its shutdown timeout, or when its
         * channel is already closed, in which case the broker redelivers it anyway.
         */
        private fun isLeftForRedelivery(error: Throwable, channel: Channel?): Boolean =
            queueProcessor.ackMode == QueueAckMode.END &&
                    (Thread.currentThread().isInterrupted ||
                            (channel != null && !channel.isOpen) ||
                            generateSequence(error) { it.cause }.any {
                                it is InterruptedException || it is InterruptedIOException || it is ClosedByInterruptException
                            })

        private fun requeueMessage(message: Message, channel: Channel?) {
            if (channel != null) {
                try {
                    logger.debug("Requeue received message {}", message)
                    channel.basicNack(message.messageProperties.deliveryTag, false, true)
                } catch (e: Exception) {
                    // The channel is closed: the broker redelivers the message anyway
                    logger.debug("Message could not be requeued (processor: ${queueProcessor.id}): ${e.message}")
                }
            }
        }

        private fun ackMessage(message: Message, channel: Channel?) {
            if (channel != null) {
                // Always acknowledge the message to prevent re-delivery
                try {
                    logger.debug("Ack received message {}", message)
                    channel.basicAck(message.messageProperties.deliveryTag, false)
                } catch (e: Exception) {
                    // Typically, the channel was closed while the message was processed, because
                    // the listener was stopped: the broker redelivers the message to another consumer
                    logAckError(e)
                }
            }
        }

        private fun logAckError(e: Exception) {
            logger.error(
                "Message could not be acked (processor: ${queueProcessor.id}): ${e.message}", e
            )
        }

    }

}

