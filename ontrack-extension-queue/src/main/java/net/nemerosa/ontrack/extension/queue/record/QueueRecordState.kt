package net.nemerosa.ontrack.extension.queue.record

/**
 * State of a queue message.
 *
 * @property isFinal True when the processing of the message is over, whatever its outcome. A
 * message redelivered by the broker while its record is in a final state is not processed again.
 */
enum class QueueRecordState(
    val isFinal: Boolean = false,
) {

    STARTED,
    ROUTING_READY,
    SENT,
    RECEIVED,
    PARSED,
    CANCELLED(isFinal = true),
    PROCESSING,
    COMPLETED(isFinal = true),
    ERRORED(isFinal = true),

}
