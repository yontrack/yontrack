package net.nemerosa.ontrack.extension.queue

/**
 * When the message received by a [QueueProcessor] is acknowledged to the broker.
 */
enum class QueueAckMode {

    /**
     * Acknowledged by the listener container once the message has been handled.
     */
    AUTO,

    /**
     * Acknowledged as soon as received, before its processing. A consumer stopped while processing
     * the message loses it: nothing redelivers it.
     */
    IMMEDIATE,

    /**
     * Acknowledged once its processing is over, successful or not. A consumer stopped while
     * processing the message leaves it unacknowledged, and the broker redelivers it to another
     * consumer. Its processing must tolerate the redelivery.
     */
    END,

}
