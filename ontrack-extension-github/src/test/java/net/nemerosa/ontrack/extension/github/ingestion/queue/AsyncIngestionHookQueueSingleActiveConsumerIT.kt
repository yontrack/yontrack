package net.nemerosa.ontrack.extension.github.ingestion.queue

import net.nemerosa.ontrack.extension.github.ingestion.AbstractIngestionTestSupport
import net.nemerosa.ontrack.it.waitUntil
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.amqp.core.AmqpTemplate
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
 * The GitHub ingestion queues are delivered to one consumer at a time, whatever the number of
 * consumers - one per backend - and fail over to another consumer when the active one goes away.
 *
 * The ingestion listener of this backend is stopped during the test, which uses two consumers
 * of its own, so that the messages are not processed.
 */
@OptIn(ExperimentalTime::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AsyncIngestionHookQueueSingleActiveConsumerIT : AbstractIngestionTestSupport() {

    @Autowired
    private lateinit var registry: RabbitListenerEndpointRegistry

    @Autowired
    private lateinit var connectionFactory: ConnectionFactory

    @Autowired
    private lateinit var amqpTemplate: AmqpTemplate

    private fun consumer(queue: String, received: MutableList<String>) =
        SimpleMessageListenerContainer(connectionFactory).apply {
            setQueueNames(queue)
            setMessageListener { message -> received += message.body.toString(Charsets.UTF_8) }
        }

    private fun publish(message: String) {
        amqpTemplate.convertAndSend(
            AsyncIngestionHookQueueConfig.TOPIC,
            AsyncIngestionHookQueueConfig.DEFAULT,
            message,
        )
    }

    @Test
    fun `Only one consumer receives the messages, in order, and another one takes over when it stops`() {
        val queue = "github.ingestion.v2.default"
        val listener = assertNotNull(
            registry.getListenerContainer(queue) as AbstractMessageListenerContainer?,
            "Ingestion listener container for the queue"
        )
        listener.stop()
        val receivedByFirst = CopyOnWriteArrayList<String>()
        val receivedBySecond = CopyOnWriteArrayList<String>()
        val first = consumer(queue, receivedByFirst)
        val second = consumer(queue, receivedBySecond)
        try {
            // The first consumer to register is the active one
            first.start()
            second.start()

            val firstMessages = (1..10).map { uid("first-") }
            firstMessages.forEach { publish(it) }
            waitUntil("First messages received", interval = 200.milliseconds, timeout = 30.seconds) {
                receivedByFirst.containsAll(firstMessages)
            }
            assertEquals(firstMessages, receivedByFirst.filter { it in firstMessages }, "Received in order")
            assertTrue(receivedBySecond.isEmpty(), "The second consumer received nothing")

            // The active consumer goes away
            first.stop()

            val secondMessages = (1..10).map { uid("second-") }
            secondMessages.forEach { publish(it) }
            waitUntil("Second messages received by the second consumer", interval = 200.milliseconds, timeout = 30.seconds) {
                receivedBySecond.containsAll(secondMessages)
            }
            assertEquals(secondMessages, receivedBySecond.filter { it in secondMessages }, "Received in order")
            assertTrue(receivedByFirst.none { it in secondMessages }, "The stopped consumer received nothing")
        } finally {
            first.stop()
            second.stop()
            listener.start()
        }
    }

}
