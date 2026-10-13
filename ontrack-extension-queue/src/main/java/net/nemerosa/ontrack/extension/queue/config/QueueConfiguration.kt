package net.nemerosa.ontrack.extension.queue.config

import net.nemerosa.ontrack.extension.queue.QueueConfigProperties
import net.nemerosa.ontrack.extension.queue.QueueProcessor
import net.nemerosa.ontrack.extension.queue.queueNamePrefix
import net.nemerosa.ontrack.extension.queue.queueRoutingPrefix
import org.springframework.amqp.core.*
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class QueueConfiguration(
        private val queueConfigProperties: QueueConfigProperties,
        private val queueProcessors: List<QueueProcessor<*>>,
) {

    @Bean
    fun queueConfigurationDeclarables(): Declarables {
        val declarables = mutableListOf<Declarable>()
        // For all processors
        queueProcessors.forEach { processor ->
            declareQueueProcessor(declarables, processor)
        }
        // OK
        return Declarables(declarables)
    }

    private fun declareQueueProcessor(
            declarables: MutableList<Declarable>,
            processor: QueueProcessor<*>
    ) {
        // One consumer at a time means one listener per queue
        check(!processor.singleActiveConsumer || processor.maxConcurrency <= 1) {
            "Queue processor ${processor.id} has a single active consumer and cannot have a maximum concurrency of ${processor.maxConcurrency}: it must be 1."
        }
        // Topic
        val topic = processor.queueNamePrefix
        val exchange = DirectExchange(topic)
        declarables += exchange

        // Default queues
        val prefix = processor.queueNamePrefix
        val scale = queueConfigProperties.getQueueProcessorScale(processor)
        (0 until scale).forEach { index ->
            val queue = if (processor.singleActiveConsumer) {
                QueueBuilder.durable("$prefix.$index").singleActiveConsumer().build()
            } else {
                Queue("$prefix.$index", true)
            }
            val binding = BindingBuilder
                    .bind(queue)
                    .to(exchange)
                    .with("${processor.queueRoutingPrefix}.$index")
            declarables += queue
            declarables += binding
        }

        // Specific queues
        processor.specificConfiguration(exchange, declarables)
    }

}