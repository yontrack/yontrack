package net.nemerosa.ontrack.extension.queue.record

import net.nemerosa.ontrack.extension.queue.QueueTestFixtures
import net.nemerosa.ontrack.extension.recordings.RecordingsQueryService
import net.nemerosa.ontrack.extension.recordings.RecordingsService
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The transitions of a queue record on the consumer side, which must tolerate the redelivery of a
 * message by the broker.
 *
 * Not transactional: the lock on a record is only visible across transactions.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class QueueRecordServiceIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var queueRecordService: QueueRecordService

    @Autowired
    private lateinit var recordingsService: RecordingsService

    @Autowired
    private lateinit var recordingsQueryService: RecordingsQueryService

    @Autowired
    private lateinit var queueRecordingsExtension: QueueRecordingsExtension

    private fun record(state: QueueRecordState): QueueRecord {
        val record = QueueTestFixtures.queueRecord(state = state, queueName = null)
        asAdmin {
            recordingsService.record(queueRecordingsExtension, record)
        }
        return record
    }

    private fun saved(id: String): QueueRecord = assertNotNull(
        asAdmin { recordingsQueryService.findById(queueRecordingsExtension, id) },
        "Record $id found"
    )

    @Test
    fun `A message is received when its record is not final`() {
        val record = record(QueueRecordState.SENT)
        val received = asAdmin {
            queueRecordService.received(record.queuePayload, "my-queue")
        }
        assertTrue(received, "Message received")
        val saved = saved(record.id)
        assertEquals(QueueRecordState.RECEIVED, saved.state)
        assertEquals("my-queue", saved.queueName)
    }

    @Test
    fun `A message is received again when its record is still processing`() {
        // The pod processing the message died: the record stays in PROCESSING and the broker
        // redelivers the message to the surviving consumer
        val record = record(QueueRecordState.PROCESSING)
        val received = asAdmin {
            queueRecordService.received(record.queuePayload, "my-queue")
        }
        assertTrue(received, "Message received")
        assertEquals(QueueRecordState.RECEIVED, saved(record.id).state)
    }

    @Test
    fun `A redelivered message whose record is final is not received and its record is left unchanged`() {
        QueueRecordState.entries.filter { it.isFinal }.forEach { state ->
            val record = record(state)
            val before = saved(record.id)
            val received = asAdmin {
                queueRecordService.received(record.queuePayload, "other-queue")
            }
            assertFalse(received, "Message in state $state not received")
            val after = saved(record.id)
            assertEquals(before.state, after.state, "State unchanged")
            assertEquals(before.history, after.history, "History unchanged")
            assertEquals(before.queueName, after.queueName, "Queue unchanged")
        }
    }

    @Test
    fun `The outcome of a superseded attempt does not overwrite a final state`() {
        val record = record(QueueRecordState.COMPLETED)
        val before = saved(record.id)
        asAdmin {
            queueRecordService.errored(record.queuePayload, RuntimeException("Zombie attempt"))
            queueRecordService.completed(record.queuePayload)
            queueRecordService.cancelled(record.queuePayload, "Zombie cancellation")
        }
        val after = saved(record.id)
        assertEquals(QueueRecordState.COMPLETED, after.state)
        assertEquals(before.history, after.history, "History unchanged")
        assertEquals(before.exception, after.exception, "No exception recorded")
    }

    @Test
    fun `Receiving a message waits for the lock on its record and sees the committed state`() {
        val record = record(QueueRecordState.PROCESSING)
        val locked = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            // First consumer: completes the record in a transaction which stays open
            val completing = executor.submit {
                inNewTransaction {
                    recordingsService.updateLockedRecord(queueRecordingsExtension, record.id) {
                        it.withState(QueueRecordState.COMPLETED)
                    }
                    locked.countDown()
                    release.await(30, TimeUnit.SECONDS)
                }
            }
            assertTrue(locked.await(30, TimeUnit.SECONDS), "Record locked")
            // Second consumer: receives the same message while the record is locked
            val receiving = executor.submit<Boolean> {
                queueRecordService.received(record.queuePayload, "other-queue")
            }
            Thread.sleep(500)
            assertFalse(receiving.isDone, "Receiving waits for the lock")
            // The first consumer commits
            release.countDown()
            completing.get(30, TimeUnit.SECONDS)
            // The second consumer sees the final state
            assertFalse(receiving.get(30, TimeUnit.SECONDS), "Message not received after the lock is released")
            assertEquals(QueueRecordState.COMPLETED, saved(record.id).state)
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

}
