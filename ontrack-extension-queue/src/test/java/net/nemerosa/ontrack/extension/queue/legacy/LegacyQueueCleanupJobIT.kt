@file:Suppress("DEPRECATION")

package net.nemerosa.ontrack.extension.queue.legacy

import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.job.Schedule
import net.nemerosa.ontrack.rabbitmq.LegacyQueueCleanupJob
import net.nemerosa.ontrack.rabbitmq.LegacyQueueCleanupResult
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.amqp.core.AmqpAdmin
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.DirectExchange
import org.springframework.amqp.core.Queue
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LegacyQueueCleanupJobIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var job: LegacyQueueCleanupJob

    @Autowired
    private lateinit var testLegacyQueues: TestLegacyQueues

    @Autowired
    private lateinit var amqpAdmin: AmqpAdmin

    @Autowired
    private lateinit var connectionFactory: ConnectionFactory

    /**
     * Only the elements of the test, others being possibly contributed
     */
    private fun LegacyQueueCleanupResult.of(prefix: String) = LegacyQueueCleanupResult(
        deletedQueues = deletedQueues.filter { it.startsWith(prefix) },
        keptQueues = keptQueues.filter { it.startsWith(prefix) },
        deletedExchanges = deletedExchanges.filter { it.startsWith(prefix) },
        keptExchanges = keptExchanges.filter { it.startsWith(prefix) },
    )

    private fun exchangeExists(name: String): Boolean =
        try {
            connectionFactory.createConnection().createChannel(false).use { it.exchangeDeclarePassive(name) }
            true
        } catch (_: Exception) {
            false
        }

    @Test
    fun `The job is registered every 10 minutes and always enabled`() {
        val registration = job.startingJobs.single()
        assertEquals(Schedule.everyMinutes(10), registration.schedule)
        assertFalse(registration.job.isDisabled)
        assertEquals("Legacy queue cleanup", registration.job.description)
    }

    @Test
    fun `Unused old queues are deleted, then their exchange, a queue with a consumer is kept`() {
        val prefix = uid("test.legacy.")
        val exchange = DirectExchange(prefix, true, false)
        val used = Queue("$prefix.used", true)
        val unused = Queue("$prefix.unused", true)
        amqpAdmin.declareExchange(exchange)
        listOf(used, unused).forEach { queue ->
            amqpAdmin.declareQueue(queue)
            amqpAdmin.declareBinding(BindingBuilder.bind(queue).to(exchange).with(queue.name))
        }
        testLegacyQueues.legacyQueues = setOf(used.name, unused.name, "$prefix.absent")
        testLegacyQueues.legacyExchanges = setOf(prefix)

        // An old backend still consumes one of the queues
        val consumer = SimpleMessageListenerContainer(connectionFactory).apply {
            setQueueNames(used.name)
            setMessageListener { }
        }
        consumer.start()
        try {
            assertEquals(
                LegacyQueueCleanupResult(
                    deletedQueues = listOf(unused.name),
                    keptQueues = listOf(used.name),
                    deletedExchanges = emptyList(),
                    keptExchanges = listOf(prefix),
                ),
                job.cleanup().of(prefix),
            )
            assertNull(amqpAdmin.getQueueProperties(unused.name), "Unused queue deleted")
            assertNotNull(amqpAdmin.getQueueProperties(used.name), "Used queue kept")
            assertEquals(true, exchangeExists(prefix), "Exchange still bound, kept")
        } finally {
            consumer.stop()
        }

        // The old backend is gone
        assertEquals(
            LegacyQueueCleanupResult(
                deletedQueues = listOf(used.name),
                keptQueues = emptyList(),
                deletedExchanges = listOf(prefix),
                keptExchanges = emptyList(),
            ),
            job.cleanup().of(prefix),
        )
        assertNull(amqpAdmin.getQueueProperties(used.name), "Queue deleted")
        assertFalse(exchangeExists(prefix), "Exchange deleted")

        // Nothing left to do
        assertEquals(
            LegacyQueueCleanupResult(
                deletedQueues = emptyList(),
                keptQueues = emptyList(),
                deletedExchanges = emptyList(),
                keptExchanges = emptyList(),
            ),
            job.cleanup().of(prefix),
        )
    }

}
