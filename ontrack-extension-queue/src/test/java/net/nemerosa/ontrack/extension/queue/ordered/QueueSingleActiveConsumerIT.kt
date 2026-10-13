package net.nemerosa.ontrack.extension.queue.ordered

import net.nemerosa.ontrack.extension.queue.QueuePayload
import net.nemerosa.ontrack.extension.queue.dispatching.QueueDispatcher
import net.nemerosa.ontrack.extension.queue.queueNamePrefix
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.waitUntil
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

/**
 * The queues of a processor with a single active consumer are delivered to one consumer at a
 * time, whatever the number of consumers - one per backend - and fail over to another consumer
 * when the active one goes away.
 *
 * Not transactional: the messages are processed on the threads of the queue listeners.
 */
@OptIn(ExperimentalTime::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class QueueSingleActiveConsumerIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var queueDispatcher: QueueDispatcher

    @Autowired
    private lateinit var processor: OrderedQueueProcessor

    @Autowired
    private lateinit var registry: RabbitListenerEndpointRegistry

    @Autowired
    private lateinit var connectionFactory: ConnectionFactory

    private fun dispatch(id: String) {
        asAdmin {
            queueDispatcher.dispatch(processor, OrderedQueuePayload(id), source = null)
        }
    }

    @Test
    fun `Only one consumer receives the messages, in order, and another one takes over when it stops`() {
        val queue = "${processor.queueNamePrefix}.0"
        // Consumer of this backend, registered first, so the active one
        val active = assertNotNull(
            registry.getListenerContainer(queue) as AbstractMessageListenerContainer?,
            "Listener container for the queue"
        )
        // Consumer of another backend
        val received = CopyOnWriteArrayList<String>()
        val other = SimpleMessageListenerContainer(connectionFactory).apply {
            setQueueNames(queue)
            setMessageListener { message ->
                val qp = QueuePayload.parse(message.body.toString(Charsets.UTF_8).parseAsJson())
                received += qp.parse(OrderedQueuePayload::class).id
            }
        }
        other.start()
        try {
            val first = (1..10).map { uid("first-") }
            first.forEach { dispatch(it) }
            waitUntil("First messages processed", interval = 200.milliseconds, timeout = 30.seconds) {
                processor.processed().containsAll(first)
            }
            assertEquals(first, processor.processed().filter { it in first }, "Processed in order")
            assertTrue(received.isEmpty(), "The other consumer received nothing")

            // The active consumer goes away
            active.stop()

            val second = (1..10).map { uid("second-") }
            second.forEach { dispatch(it) }
            waitUntil("Second messages received by the other consumer", interval = 200.milliseconds, timeout = 30.seconds) {
                received.containsAll(second)
            }
            assertEquals(second, received.toList(), "Received in order, by the other consumer only")
            assertTrue(processor.processed().none { it in second }, "The stopped consumer received nothing")
        } finally {
            other.stop()
            active.start()
        }
    }

}
