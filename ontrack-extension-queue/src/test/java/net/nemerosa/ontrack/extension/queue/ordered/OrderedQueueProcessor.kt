package net.nemerosa.ontrack.extension.queue.ordered

import net.nemerosa.ontrack.extension.queue.QueueMetadata
import net.nemerosa.ontrack.extension.queue.QueueProcessor
import org.springframework.stereotype.Component
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KClass

/**
 * Queue processor with a single active consumer, which records the messages it processes, in
 * the order of their processing.
 */
@Component
class OrderedQueueProcessor : QueueProcessor<OrderedQueuePayload> {

    private val processed = CopyOnWriteArrayList<String>()

    /**
     * IDs of the processed messages, in the order of their processing.
     */
    fun processed(): List<String> = processed.toList()

    override val id: String = "test-ordered"

    override val singleActiveConsumer: Boolean = true

    override val payloadType: KClass<OrderedQueuePayload> = OrderedQueuePayload::class

    override fun isCancelled(payload: OrderedQueuePayload): String? = null

    override fun getRoutingIdentifier(payload: OrderedQueuePayload): String = payload.id

    override fun process(payload: OrderedQueuePayload, queueMetadata: QueueMetadata?) {
        processed += payload.id
    }
}

/**
 * @property id ID of the message
 */
data class OrderedQueuePayload(
    val id: String,
)
