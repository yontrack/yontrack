package net.nemerosa.ontrack.extension.workflows.engine

import jakarta.annotation.PreDestroy
import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.workflows.WorkflowConfigurationProperties
import net.nemerosa.ontrack.extension.workflows.repository.WorkflowEngineProcessRepository
import net.nemerosa.ontrack.model.tx.DefaultTransactionHelper
import net.nemerosa.ontrack.model.tx.TransactionRetry
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * This Yontrack process, as the owner of the workflow nodes it runs.
 *
 * Its heartbeat tells the other processes that its nodes are alive: once it stops, they recover
 * them (see `WorkflowNodeRecoveryService`).
 *
 * The heartbeat runs on its own thread, not as a job: a paused job would let the other processes
 * recover nodes which are still running.
 */
@Component
class WorkflowEngineProcess(
    private val workflowEngineProcessRepository: WorkflowEngineProcessRepository,
    private val workflowConfigurationProperties: WorkflowConfigurationProperties,
    platformTransactionManager: PlatformTransactionManager,
    transactionRetry: TransactionRetry,
) {

    private val logger: Logger = LoggerFactory.getLogger(WorkflowEngineProcess::class.java)

    private val transactionHelper = DefaultTransactionHelper(platformTransactionManager, transactionRetry)

    /**
     * Unique ID of this process
     */
    val id: String = UUID.randomUUID().toString()

    /**
     * Start of this process
     */
    val started: LocalDateTime = Time.now

    @Volatile
    private var registered = false

    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "workflow-engine-heartbeat").apply { isDaemon = true }
    }

    /**
     * ID to record as the owner of a node. The process is registered first, so that a node never
     * has an owner unknown to the other processes, which would take it as stopped.
     */
    fun ownerId(): String {
        if (!registered) {
            heartbeat()
        }
        return id
    }

    private fun heartbeat() {
        transactionHelper.inNewTransaction {
            workflowEngineProcessRepository.heartbeat(id = id, started = started, heartbeat = Time.now)
        }
        registered = true
    }

    @EventListener(ApplicationReadyEvent::class)
    fun start() {
        val interval = workflowConfigurationProperties.recovery.heartbeat.toMillis()
        scheduler.scheduleWithFixedDelay(
            {
                try {
                    heartbeat()
                } catch (any: Throwable) {
                    logger.error("Workflow engine heartbeat failed", any)
                }
            },
            0,
            interval,
            TimeUnit.MILLISECONDS,
        )
    }

    /**
     * The process row is left in place: deleting it would let the other processes recover nodes
     * which this process may still be finishing. It goes stale instead.
     */
    @PreDestroy
    fun stop() {
        scheduler.shutdownNow()
    }
}
