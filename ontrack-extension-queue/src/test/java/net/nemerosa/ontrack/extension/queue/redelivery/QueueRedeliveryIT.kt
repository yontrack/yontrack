package net.nemerosa.ontrack.extension.queue.redelivery

import io.micrometer.core.instrument.MeterRegistry
import net.nemerosa.ontrack.extension.queue.dispatching.QueueDispatcher
import net.nemerosa.ontrack.extension.queue.metrics.QueueMetrics
import net.nemerosa.ontrack.extension.queue.queueNamePrefix
import net.nemerosa.ontrack.extension.queue.record.QueueRecord
import net.nemerosa.ontrack.extension.queue.record.QueueRecordState
import net.nemerosa.ontrack.extension.queue.record.QueueRecordingsExtension
import net.nemerosa.ontrack.extension.recordings.RecordingsQueryService
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.waitUntil
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.format
import net.nemerosa.ontrack.rabbitmq.OntrackRabbitMQConfigProperties
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

/**
 * A message is acknowledged only once its processing has completed: a consumer stopping with a
 * message in hand waits for its processing to complete, and past its shutdown timeout, leaves it
 * to the broker, which redelivers it. A redelivery of a message which was already processed is
 * skipped.
 *
 * Not transactional: the messages are processed on the threads of the queue listeners.
 */
@OptIn(ExperimentalTime::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class QueueRedeliveryIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var queueDispatcher: QueueDispatcher

    @Autowired
    private lateinit var processor: RedeliveryQueueProcessor

    @Autowired
    private lateinit var registry: RabbitListenerEndpointRegistry

    @Autowired
    private lateinit var amqpTemplate: AmqpTemplate

    @Autowired
    private lateinit var meterRegistry: MeterRegistry

    @Autowired
    private lateinit var recordingsQueryService: RecordingsQueryService

    @Autowired
    private lateinit var ontrackRabbitMQConfigProperties: OntrackRabbitMQConfigProperties

    @Autowired
    private lateinit var queueRecordingsExtension: QueueRecordingsExtension

    private fun dispatch(id: String): String = assertNotNull(
        asAdmin {
            queueDispatcher.dispatch(processor, RedeliveryQueuePayload(id), source = null).id
        },
        "Message queued"
    )

    private fun record(recordId: String): QueueRecord = assertNotNull(
        asAdmin { recordingsQueryService.findById(queueRecordingsExtension, recordId) },
        "Record $recordId found"
    )

    private fun waitForRecordState(recordId: String, state: QueueRecordState) {
        waitUntil("Record $recordId in state $state", interval = 200.milliseconds, timeout = 30.seconds) {
            record(recordId).state == state
        }
    }

    private fun container(): AbstractMessageListenerContainer = assertNotNull(
        registry.getListenerContainer("${processor.queueNamePrefix}.0") as AbstractMessageListenerContainer?,
        "Listener container for the queue"
    )

    private fun receivedCount(): Double =
        meterRegistry.find(QueueMetrics.messageReceived)
            .tag("processor", processor.id)
            .counter()
            ?.count()
            ?: 0.0

    @Test
    fun `A message whose processing is interrupted is redelivered and processed to completion exactly once`() {
        val id = uid("m-")
        processor.holdFirstAttempt(id)
        val recordId = dispatch(id)
        waitUntil("First attempt started", interval = 200.milliseconds, timeout = 30.seconds) {
            processor.attempts(id) == 1
        }

        // The consumer stops with the message in hand, like a pod being stopped: it waits for the
        // message for a while, then closes its channel, interrupts the processing, and the broker
        // redelivers the message
        val container = container()
        // Not waiting for the whole shutdown timeout
        container.setShutdownTimeout(1_000)
        try {
            container.stop()
        } finally {
            container.setShutdownTimeout(ontrackRabbitMQConfigProperties.listenerShutdownTimeout.toMillis())
        }
        container.start()

        // The redelivery is processed to completion
        waitUntil("Redelivery processed", interval = 200.milliseconds, timeout = 30.seconds) {
            processor.completions(id) == 1
        }
        waitForRecordState(recordId, QueueRecordState.COMPLETED)

        // The interrupted attempt is over, and its failure was not recorded
        processor.abortHeldAttempt(id)
        waitUntil("Interrupted attempt ended", interval = 200.milliseconds, timeout = 30.seconds) {
            processor.ended(id) == 2
        }
        Thread.sleep(500)

        assertEquals(2, processor.attempts(id), "Two attempts")
        assertEquals(1, processor.completions(id), "One completion")
        val record = record(recordId)
        assertEquals(QueueRecordState.COMPLETED, record.state)
        assertEquals(1, record.history.count { it.state == QueueRecordState.COMPLETED }, "Completed once")
        assertTrue(record.history.none { it.state == QueueRecordState.ERRORED }, "No error recorded")
    }

    @Test
    fun `A message in processing when its listener stops is processed to completion and acked, not redelivered`() {
        val id = uid("m-")
        // Longer than the default shutdown timeout of Spring AMQP (5 seconds), shorter than ours
        processor.lastFirstAttempt(id, Duration.ofSeconds(8))
        val recordId = dispatch(id)
        waitUntil("Processing started", interval = 200.milliseconds, timeout = 30.seconds) {
            processor.attempts(id) == 1
        }
        val received = receivedCount()

        // The listener stops with the message in hand, like when Yontrack stops: when the context
        // closes, the registry of the listeners stops each of them like this, and waits for them
        // for the timeout of the shutdown phase
        val container = container()
        val stopped = CountDownLatch(1)
        container.stop { stopped.countDown() }
        assertTrue(stopped.await(45, TimeUnit.SECONDS), "Listener stopped within the shutdown phase")
        assertEquals(1, processor.completions(id), "Processed to completion before the listener stopped")
        waitForRecordState(recordId, QueueRecordState.COMPLETED)

        // Acked: the broker does not deliver it again
        container.start()
        Thread.sleep(2_000)
        assertEquals(received, receivedCount(), "Not delivered again")
        assertEquals(1, processor.attempts(id), "Processed once")
        assertEquals(1, processor.completions(id), "Completed once")
    }

    @Test
    fun `A redelivered message whose record is already completed is skipped without side effects`() {
        val id = uid("m-")
        val recordId = dispatch(id)
        waitUntil("Message processed", interval = 200.milliseconds, timeout = 30.seconds) {
            processor.completions(id) == 1
        }
        waitForRecordState(recordId, QueueRecordState.COMPLETED)
        val before = record(recordId)

        // The broker redelivers the message: same body, same queue
        val receivedBefore = receivedCount()
        amqpTemplate.convertAndSend(
            processor.queueNamePrefix,
            assertNotNull(before.routingKey, "Routing key"),
            before.queuePayload.asJson().format(),
        )
        waitUntil("Redelivery received", interval = 200.milliseconds, timeout = 30.seconds) {
            receivedCount() >= receivedBefore + 1
        }
        Thread.sleep(500)

        assertEquals(1, processor.attempts(id), "Not processed again")
        val after = record(recordId)
        assertEquals(before.state, after.state, "State unchanged")
        assertEquals(before.history, after.history, "History unchanged")
        assertEquals(before.queueName, after.queueName, "Queue unchanged")
        assertEquals(before.endTime, after.endTime, "End time unchanged")
    }

}
