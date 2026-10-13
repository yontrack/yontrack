package net.nemerosa.ontrack.extension.queue.memo

import net.nemerosa.ontrack.extension.queue.dispatching.QueueDispatcher
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.waitUntil
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

/**
 * The processing of each queue message is a unit of work of its own, with its read memo (#2080).
 *
 * Not transactional: the messages are processed on the threads of the queue listeners.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class QueueReadMemoIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var queueDispatcher: QueueDispatcher

    @Autowired
    private lateinit var readMemoQueueProcessor: ReadMemoQueueProcessor

    @OptIn(ExperimentalTime::class)
    private fun processed(id: String): ReadMemoState {
        waitUntil("Message $id processed", interval = 200.milliseconds, timeout = 30.seconds) {
            readMemoQueueProcessor.memo(id) != null
        }
        return readMemoQueueProcessor.memo(id)!!
    }

    @Test
    fun `Each message is processed in a read memo of its own`() {
        val first = uid("m-")
        val second = uid("m-")
        asAdmin {
            queueDispatcher.dispatch(readMemoQueueProcessor, ReadMemoQueuePayload(first), source = null)
        }
        assertEquals(ReadMemoState(open = true, previous = null), processed(first))
        asAdmin {
            queueDispatcher.dispatch(readMemoQueueProcessor, ReadMemoQueuePayload(second), source = null)
        }
        assertEquals(
            ReadMemoState(open = true, previous = null),
            processed(second),
            "The memo of the first message is gone"
        )
    }
}
