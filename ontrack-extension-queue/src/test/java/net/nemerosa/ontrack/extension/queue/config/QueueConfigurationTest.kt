package net.nemerosa.ontrack.extension.queue.config

import net.nemerosa.ontrack.extension.queue.QueueConfigProperties
import net.nemerosa.ontrack.extension.queue.QueueMetadata
import net.nemerosa.ontrack.extension.queue.QueueProcessor
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.DirectExchange
import org.springframework.amqp.core.Queue
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QueueConfigurationTest {

    private class TestProcessor(
        override val id: String,
        override val singleActiveConsumer: Boolean = false,
        override val maxConcurrency: Int = 1,
        override val defaultScale: Int? = null,
    ) : QueueProcessor<String> {
        override fun getRoutingIdentifier(payload: String): String = payload
        override fun process(payload: String, queueMetadata: QueueMetadata?) {}
        override fun isCancelled(payload: String): String? = null
        override val payloadType: KClass<String> = String::class
    }

    private fun declarables(processor: QueueProcessor<*>) =
        QueueConfiguration(QueueConfigProperties(), listOf(processor)).queueConfigurationDeclarables()

    @Test
    fun `A processor without a single active consumer keeps its names and has no argument`() {
        val declarables = declarables(TestProcessor("test", defaultScale = 2))
        assertEquals(
            listOf("ontrack.queue.test"),
            declarables.getDeclarablesByType(DirectExchange::class.java).map { it.name },
        )
        val queues = declarables.getDeclarablesByType(Queue::class.java)
        assertEquals(listOf("ontrack.queue.test.0", "ontrack.queue.test.1"), queues.map { it.name })
        queues.forEach { assertNull(it.arguments["x-single-active-consumer"]) }
        assertEquals(
            listOf("ontrack.queue.test.routing.0", "ontrack.queue.test.routing.1"),
            declarables.getDeclarablesByType(Binding::class.java).map { it.routingKey },
        )
    }

    @Test
    fun `A processor with a single active consumer has new names and the argument`() {
        val declarables = declarables(TestProcessor("test", singleActiveConsumer = true, defaultScale = 2))
        assertEquals(
            listOf("ontrack.queue.test.v2"),
            declarables.getDeclarablesByType(DirectExchange::class.java).map { it.name },
        )
        val queues = declarables.getDeclarablesByType(Queue::class.java)
        assertEquals(listOf("ontrack.queue.test.v2.0", "ontrack.queue.test.v2.1"), queues.map { it.name })
        queues.forEach {
            assertEquals(true, it.arguments["x-single-active-consumer"])
            assertEquals(true, it.isDurable)
        }
        val bindings = declarables.getDeclarablesByType(Binding::class.java)
        assertEquals(listOf("ontrack.queue.test.v2.routing.0", "ontrack.queue.test.v2.routing.1"), bindings.map { it.routingKey })
        bindings.forEach { assertEquals("ontrack.queue.test.v2", it.exchange) }
    }

    @Test
    fun `A processor with a single active consumer and a maximum concurrency above 1 is refused`() {
        val error = assertThrows<IllegalStateException> {
            declarables(TestProcessor("test", singleActiveConsumer = true, maxConcurrency = 2))
        }
        assertEquals(
            "Queue processor test has a single active consumer and cannot have a maximum concurrency of 2: it must be 1.",
            error.message,
        )
    }

}
