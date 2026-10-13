package net.nemerosa.ontrack.extension.queue.dispatching

import net.nemerosa.ontrack.extension.queue.QueueProcessor
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * Dispatches a message once the current transaction is committed, so that its processing — on any
 * replica — reads what this transaction wrote. Nothing is dispatched for a transaction which is
 * rolled back. Without a transaction, the message is dispatched at once.
 *
 * Meant for work which an event listener triggers and which computes from the current state of the
 * database (#2083).
 *
 * The dispatch runs in a transaction of its own: the one which was just committed can no longer
 * write anything. Its failure is logged, and never reaches the caller, whose transaction is
 * committed already.
 */
@Component
class QueuePostCommitDispatcher(
    private val queueDispatcher: QueueDispatcher,
    transactionManager: PlatformTransactionManager,
) {

    private val logger: Logger = LoggerFactory.getLogger(QueuePostCommitDispatcher::class.java)

    private val newTransaction = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

    fun <T : Any> dispatchAfterCommit(queueProcessor: QueueProcessor<T>, payload: T) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() {
                    dispatch(queueProcessor, payload)
                }
            })
        } else {
            dispatch(queueProcessor, payload)
        }
    }

    private fun <T : Any> dispatch(queueProcessor: QueueProcessor<T>, payload: T) {
        try {
            newTransaction.executeWithoutResult {
                queueDispatcher.dispatch(queueProcessor, payload, source = null)
            }
        } catch (any: Exception) {
            logger.error("Cannot dispatch the message to the ${queueProcessor.id} queue: $payload", any)
        }
    }
}
