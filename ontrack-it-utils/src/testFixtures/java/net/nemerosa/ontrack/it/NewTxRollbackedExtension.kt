package net.nemerosa.ontrack.it

import org.junit.jupiter.api.extension.AfterTestExecutionCallback
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.DefaultTransactionDefinition


class NewTxRollbackedExtension : BeforeTestExecutionCallback, AfterTestExecutionCallback {

    private val logger: Logger = LoggerFactory.getLogger(NewTxRollbackedExtension::class.java)

    override fun beforeTestExecution(exc: ExtensionContext) {
        val transactionManager = getTransactionManager(exc)
        val definition = DefaultTransactionDefinition()
        definition.propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        val status = transactionManager.getTransaction(definition)
        exc.getStore(namespace).put(TX_STATUS, status)
        logger.info("[${exc.displayName}] TX NEW")
    }

    override fun afterTestExecution(exc: ExtensionContext) {
        val transactionManager = getTransactionManager(exc)
        val status = exc.getStore(namespace).get(TX_STATUS, TransactionStatus::class.java)
            ?: error("No transaction status stored for the test")
        transactionManager.rollback(status)
        logger.info("[${exc.displayName}] TX ROLLBACK")
    }

    companion object {

        private fun getTransactionManager(exc: ExtensionContext): PlatformTransactionManager {
            val applicationContext = SpringExtension.getApplicationContext(exc)
            return applicationContext.getBean(PlatformTransactionManager::class.java)
        }

        private val namespace: ExtensionContext.Namespace =
            ExtensionContext.Namespace.create(NewTxRollbackedExtension::class.java)

        private const val TX_STATUS = "txStatus"
    }
}