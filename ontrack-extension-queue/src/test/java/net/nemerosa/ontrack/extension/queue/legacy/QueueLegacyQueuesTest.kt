@file:Suppress("DEPRECATION")

package net.nemerosa.ontrack.extension.queue.legacy

import net.nemerosa.ontrack.extension.queue.QueueConfigProperties
import net.nemerosa.ontrack.extension.queue.QueueMetadata
import net.nemerosa.ontrack.extension.queue.QueueProcessor
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass
import kotlin.test.assertEquals

class QueueLegacyQueuesTest {

    private class TestProcessor(
        override val id: String,
        override val singleActiveConsumer: Boolean,
    ) : QueueProcessor<String> {
        override fun getRoutingIdentifier(payload: String): String = payload
        override fun process(payload: String, queueMetadata: QueueMetadata?) {}
        override fun isCancelled(payload: String): String? = null
        override val payloadType: KClass<String> = String::class
    }

    private val processors = listOf(
        TestProcessor("ordered", singleActiveConsumer = true),
        TestProcessor("unordered", singleActiveConsumer = false),
    )

    @Test
    fun `Old names of the processors with a single active consumer, probing 32 queues`() {
        val legacy = QueueLegacyQueues(processors, QueueConfigProperties())
        assertEquals(setOf("ontrack.queue.ordered"), legacy.legacyExchanges)
        assertEquals((0 until 32).map { "ontrack.queue.ordered.$it" }.toSet(), legacy.legacyQueues)
    }

    @Test
    fun `Old names beyond the probe from the configured scale`() {
        val properties = QueueConfigProperties().apply {
            specific["ordered"] = QueueConfigProperties.SpecificProperties().apply { scale = 40 }
        }
        val legacy = QueueLegacyQueues(processors, properties)
        assertEquals((0 until 40).map { "ontrack.queue.ordered.$it" }.toSet(), legacy.legacyQueues)
    }

}
