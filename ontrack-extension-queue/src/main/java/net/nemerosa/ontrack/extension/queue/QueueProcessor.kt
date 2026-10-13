package net.nemerosa.ontrack.extension.queue

import org.springframework.amqp.core.Declarable
import org.springframework.amqp.core.DirectExchange
import kotlin.reflect.KClass

interface QueueProcessor<T : Any> {

    /**
     * Unique ID
     */
    val id: String

    /**
     * Returns true if the processing must be done synchronously
     */
    val sync: Boolean get() = false

    /**
     * Default number of queues to create (defaults to 1 if not specified).
     *
     * It can always be overridden at properties level.
     */
    val defaultScale: Int? get() = null

    /**
     * Minimal number of concurrent listeners on a queue
     */
    val minConcurrency: Int get() = 1

    /**
     * Maximum number of concurrent listeners on a queue
     */
    val maxConcurrency: Int get() = 1

    /**
     * True when the messages of a queue must be processed in order, by one consumer at a time,
     * whatever the number of backends: the queues are declared with `x-single-active-consumer`,
     * and the broker fails over to another consumer when the active one goes away.
     *
     * The queues of such a processor have their own names, [queueNamePrefix] ending with `.v2`,
     * since the arguments of an existing queue cannot change.
     *
     * Its [maxConcurrency] must be 1.
     */
    val singleActiveConsumer: Boolean get() = false

    /**
     * Gets a specific routing key for the payload.
     *
     * Returns null by default, to rely on the global settings
     *
     * @param payload The payload to route
     * @return A routing key or `null` if the default routing key must be used
     */
    fun getSpecificRoutingKey(payload: T): String? = null

    /**
     * When there is no [specific routing key][getSpecificRoutingKey], this function
     * is called to return the identifier of the payload used to spread the load
     * among the available queues.
     */
    fun getRoutingIdentifier(payload: T): String

    /**
     * Processes the payload.
     *
     * @param payload Message payload
     * @param queueMetadata Metadata for the queue. Null if not available.
     */
    fun process(payload: T, queueMetadata: QueueMetadata?)

    /**
     * Performs some specific configuration for the queues.
     *
     * @param exchange Exchange to be used
     * @param declarables List of AMQP declarables to complete.
     */
    fun specificConfiguration(
        exchange: DirectExchange,
        declarables: MutableList<Declarable>,
    ) {
    }

    /**
     * If the payload cannot be processed, cancels it.
     *
     * @param payload Payload to be processed
     * @return A non-null reason is the payload must be cancelled.
     */
    fun isCancelled(payload: T): String?

    /**
     * Type of the payload
     */
    val payloadType: KClass<T>

    /**
     * Ack mode
     */
    val ackMode: QueueAckMode get() = QueueAckMode.AUTO

}