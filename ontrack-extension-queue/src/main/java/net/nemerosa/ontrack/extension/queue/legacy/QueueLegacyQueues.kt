@file:Suppress("DEPRECATION")

package net.nemerosa.ontrack.extension.queue.legacy

import net.nemerosa.ontrack.extension.queue.QueueConfigProperties
import net.nemerosa.ontrack.extension.queue.QueueProcessor
import net.nemerosa.ontrack.rabbitmq.LegacyQueues
import org.springframework.stereotype.Component

/**
 * Former queues and exchanges of the processors with a
 * [single active consumer][QueueProcessor.singleActiveConsumer], whose queues were renamed.
 *
 * Their number is the configured one, or at least [LegacyQueues.PROBED_QUEUE_COUNT], since the scale of the
 * previous version is unknown.
 */
@Deprecated("Removed in V7. No replacement. See #2091")
@Component
class QueueLegacyQueues(
    queueProcessors: List<QueueProcessor<*>>,
    queueConfigProperties: QueueConfigProperties,
) : LegacyQueues {

    private val processors = queueProcessors.filter { it.singleActiveConsumer }

    override val legacyQueues: Set<String> = processors.flatMap { processor ->
        val count = maxOf(LegacyQueues.PROBED_QUEUE_COUNT, queueConfigProperties.getQueueProcessorScale(processor))
        (0 until count).map { index -> "${processor.legacyPrefix}.$index" }
    }.toSet()

    override val legacyExchanges: Set<String> = processors.map { it.legacyPrefix }.toSet()

    private val QueueProcessor<*>.legacyPrefix: String get() = "ontrack.queue.$id"

}
