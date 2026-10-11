package net.nemerosa.ontrack.extension.queue.record

import net.nemerosa.ontrack.extension.queue.QueuePayload
import net.nemerosa.ontrack.extension.queue.source.QueueSource

/**
 * Recording events on queue items.
 *
 * The events of the consumer side ([received] and after) may be recorded more than once for the
 * same message, because the broker redelivers a message whose consumer stopped before its
 * processing was over. Each of them is a transition of the record under a lock on it, which
 * leaves a record in a [final][QueueRecordState.isFinal] state unchanged.
 */
interface QueueRecordService {

    fun start(queuePayload: QueuePayload, source: QueueSource?)
    fun setRouting(queuePayload: QueuePayload, routingKey: String)
    fun sent(queuePayload: QueuePayload)

    /**
     * Records the reception of a message by a consumer.
     *
     * @return `false` when the processing of this message is already over, and the message must
     * be skipped: it is a redelivery
     */
    fun received(queuePayload: QueuePayload, queue: String?): Boolean
    fun parsed(queuePayload: QueuePayload, payload: Any)
    fun cancelled(queuePayload: QueuePayload, cancelReason: String)
    fun processing(queuePayload: QueuePayload)
    fun completed(queuePayload: QueuePayload)
    fun errored(queuePayload: QueuePayload, exception: Exception)

}
