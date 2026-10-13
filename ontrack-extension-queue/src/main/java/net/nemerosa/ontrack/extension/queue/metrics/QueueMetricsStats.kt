package net.nemerosa.ontrack.extension.queue.metrics

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import net.nemerosa.ontrack.extension.queue.QueueConfigProperties
import net.nemerosa.ontrack.extension.queue.QueueProcessor
import net.nemerosa.ontrack.extension.queue.queueNamePrefix
import net.nemerosa.ontrack.model.support.StartupService
import org.springframework.amqp.core.AmqpAdmin
import org.springframework.stereotype.Component
import java.util.*

@Component
class QueueMetricsStats(
        private val queueProcessors: List<QueueProcessor<*>>,
        private val queueConfigProperties: QueueConfigProperties,
        private val amqpAdmin: AmqpAdmin,
        private val registry: MeterRegistry,
) : StartupService {

    override fun getName(): String = "Queue metrics"

    override fun startupOrder(): Int = StartupService.JOB_REGISTRATION

    override fun start() {
        queueProcessors.forEach { queueProcessor ->
            registry.gauge(QueueMetrics.pending, listOf(
                    Tag.of("processor", queueProcessor.id)
            ), registry) {
                var count = 0
                val scale = queueConfigProperties.getQueueProcessorScale(queueProcessor)
                (0 until scale).forEach { index ->
                    val queueName = "${queueProcessor.queueNamePrefix}.$index"
                    val queueProperties: Properties? = amqpAdmin.getQueueProperties(queueName)
                    count += queueProperties?.get("QUEUE_MESSAGE_COUNT")?.toString()?.toInt(10) ?: 0
                }
                count.toDouble()
            }
        }
    }


}