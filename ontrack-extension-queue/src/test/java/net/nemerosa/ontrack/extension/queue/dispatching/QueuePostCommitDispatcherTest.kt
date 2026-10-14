package net.nemerosa.ontrack.extension.queue.dispatching

import io.mockk.mockk
import net.nemerosa.ontrack.extension.queue.QueueProcessor
import net.nemerosa.ontrack.extension.queue.source.QueueSource
import org.junit.jupiter.api.Test
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import kotlin.test.assertEquals

class QueuePostCommitDispatcherTest {

    private data class TestPayload(val id: Int)

    private val first = mockk<QueueProcessor<TestPayload>>()
    private val second = mockk<QueueProcessor<TestPayload>>()

    /**
     * Records the messages dispatched to the queues
     */
    private val dispatched = mutableListOf<Pair<QueueProcessor<*>, Any>>()

    private val queueDispatcher = object : QueueDispatcher {
        override fun <T : Any> dispatch(
            queueProcessor: QueueProcessor<T>,
            payload: T,
            source: QueueSource?,
            routingFeedback: (routingKey: String) -> Unit,
        ): QueueDispatchResult {
            dispatched += queueProcessor to payload
            return QueueDispatchResult(type = QueueDispatchResultType.PROCESSED, id = null)
        }
    }

    /**
     * Transaction manager with no resource, which runs the synchronizations of its transactions
     */
    private val transactionManager = object : AbstractPlatformTransactionManager() {
        override fun doGetTransaction(): Any = Any()
        override fun doBegin(transaction: Any, definition: TransactionDefinition) {}
        override fun doCommit(status: DefaultTransactionStatus) {}
        override fun doRollback(status: DefaultTransactionStatus) {}
    }

    private val dispatcher = QueuePostCommitDispatcher(queueDispatcher, transactionManager)

    private fun inTransaction(rollback: Boolean = false, code: () -> Unit) {
        val before = dispatched.toList()
        TransactionTemplate(transactionManager).executeWithoutResult { status ->
            code()
            // Nothing dispatched before the commit
            assertEquals(before, dispatched)
            if (rollback) {
                status.setRollbackOnly()
            }
        }
    }

    @Test
    fun `Equal payloads for the same processor in one transaction are dispatched once`() {
        inTransaction {
            dispatcher.dispatchAfterCommit(first, TestPayload(1))
            dispatcher.dispatchAfterCommit(first, TestPayload(1))
            dispatcher.dispatchAfterCommit(first, TestPayload(1))
        }
        assertEquals(listOf<Pair<QueueProcessor<*>, Any>>(first to TestPayload(1)), dispatched)
    }

    @Test
    fun `Different payloads or processors in one transaction are each dispatched once`() {
        inTransaction {
            dispatcher.dispatchAfterCommit(first, TestPayload(1))
            dispatcher.dispatchAfterCommit(first, TestPayload(2))
            dispatcher.dispatchAfterCommit(second, TestPayload(1))
            dispatcher.dispatchAfterCommit(first, TestPayload(2))
            dispatcher.dispatchAfterCommit(second, TestPayload(1))
        }
        assertEquals(
            listOf<Pair<QueueProcessor<*>, Any>>(
                first to TestPayload(1),
                first to TestPayload(2),
                second to TestPayload(1),
            ),
            dispatched
        )
    }

    @Test
    fun `Equal payloads in two transactions are dispatched once each`() {
        inTransaction { dispatcher.dispatchAfterCommit(first, TestPayload(1)) }
        inTransaction { dispatcher.dispatchAfterCommit(first, TestPayload(1)) }
        assertEquals(
            listOf<Pair<QueueProcessor<*>, Any>>(first to TestPayload(1), first to TestPayload(1)),
            dispatched
        )
    }

    @Test
    fun `Nothing is dispatched for a rolled back transaction`() {
        inTransaction(rollback = true) {
            dispatcher.dispatchAfterCommit(first, TestPayload(1))
            dispatcher.dispatchAfterCommit(second, TestPayload(2))
        }
        assertEquals(emptyList(), dispatched)
    }

    @Test
    fun `Without a transaction, every message is dispatched at once`() {
        dispatcher.dispatchAfterCommit(first, TestPayload(1))
        assertEquals(listOf<Pair<QueueProcessor<*>, Any>>(first to TestPayload(1)), dispatched)
        dispatcher.dispatchAfterCommit(first, TestPayload(1))
        assertEquals(
            listOf<Pair<QueueProcessor<*>, Any>>(first to TestPayload(1), first to TestPayload(1)),
            dispatched
        )
    }
}
