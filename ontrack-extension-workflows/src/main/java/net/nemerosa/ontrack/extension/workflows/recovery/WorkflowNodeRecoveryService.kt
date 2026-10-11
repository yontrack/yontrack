package net.nemerosa.ontrack.extension.workflows.recovery

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.workflows.WorkflowConfigurationProperties
import net.nemerosa.ontrack.extension.workflows.engine.WorkflowEngineProcess
import net.nemerosa.ontrack.extension.workflows.engine.WorkflowInstanceLauncher
import net.nemerosa.ontrack.extension.workflows.engine.WorkflowNodeDispatcher
import net.nemerosa.ontrack.extension.workflows.repository.WorkflowEngineProcessRepository
import net.nemerosa.ontrack.extension.workflows.repository.WorkflowInstanceRepository
import net.nemerosa.ontrack.model.security.AccountService
import net.nemerosa.ontrack.model.security.AuthenticationStorageService
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.tx.DefaultTransactionHelper
import net.nemerosa.ontrack.model.tx.TransactionRetry
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import java.time.LocalDateTime

/**
 * Recovers the workflow nodes left unfinished by a stopped Yontrack process (see
 * [WorkflowNodeRecoveryPolicy] for the rules).
 *
 * Several processes may recover at the same time: a node is changed only if it is still as the
 * recovery saw it, so that only one of them acts on it.
 */
@Component
class WorkflowNodeRecoveryService(
    private val workflowInstanceRepository: WorkflowInstanceRepository,
    private val workflowEngineProcessRepository: WorkflowEngineProcessRepository,
    private val workflowEngineProcess: WorkflowEngineProcess,
    private val workflowNodeDispatcher: WorkflowNodeDispatcher,
    private val workflowConfigurationProperties: WorkflowConfigurationProperties,
    private val securityService: SecurityService,
    private val accountService: AccountService,
    private val authenticationStorageService: AuthenticationStorageService,
    platformTransactionManager: PlatformTransactionManager,
    transactionRetry: TransactionRetry,
) {

    private val logger: Logger = LoggerFactory.getLogger(WorkflowNodeRecoveryService::class.java)

    private val transactionHelper = DefaultTransactionHelper(platformTransactionManager, transactionRetry)

    /**
     * @param now Current time
     * @param processStarted Start of this process
     */
    fun recover(
        now: LocalDateTime = Time.now,
        processStarted: LocalDateTime = workflowEngineProcess.started,
    ) {
        val config = workflowConfigurationProperties.recovery
        val (storedHeartbeats, candidates) = transactionHelper.inNewTransaction {
            workflowEngineProcessRepository.getHeartbeats() to workflowInstanceRepository.findRecoveryCandidates()
        }
        // This process is alive, whatever its stored heartbeat says: recovering its own nodes would
        // let it claim them again, under the same owner, and run them twice
        val heartbeats = storedHeartbeats + (workflowEngineProcess.id to now)
        candidates.forEach { candidate ->
            val action = WorkflowNodeRecoveryPolicy.decide(
                candidate = candidate,
                heartbeats = heartbeats,
                now = now,
                processStarted = processStarted,
                config = config,
            )
            try {
                when (action) {
                    WorkflowNodeRecoveryAction.None -> {}
                    WorkflowNodeRecoveryAction.Resume -> resume(candidate, now)
                    is WorkflowNodeRecoveryAction.Fail -> fail(candidate, action.message, now)
                }
            } catch (any: Exception) {
                // The other nodes are still to be recovered
                logger.error(
                    "Workflow node recovery failed for instance ${candidate.instanceId}, node ${candidate.nodeId}",
                    any
                )
            }
        }
        transactionHelper.inNewTransaction {
            workflowEngineProcessRepository.prune(now - config.maxAge)
        }
    }

    private fun resume(candidate: WorkflowNodeRecoveryCandidate, now: LocalDateTime) {
        val launcher = candidate.launcher ?: error("Launcher is required to resume a node")
        if (launcher.account != null && !accountExists(launcher.account)) {
            fail(candidate, WorkflowNodeRecoveryPolicy.LAUNCHER_GONE, now)
            return
        }
        val reset = transactionHelper.inNewTransaction {
            workflowInstanceRepository.resetNode(candidate, now)
        }
        if (reset) {
            logger.info(
                "Workflow node recovery: dispatching again instance {}, node {} ({}, owner = {})",
                candidate.instanceId,
                candidate.nodeId,
                candidate.status,
                candidate.owner,
            )
            // If this process stops before the dispatch, the node is left CREATED, and dispatched
            // again by the next recovery
            asLauncher(launcher) {
                workflowNodeDispatcher.dispatch(candidate.instanceId, candidate.nodeId)
            }
        }
    }

    private fun fail(candidate: WorkflowNodeRecoveryCandidate, message: String, now: LocalDateTime) {
        val failed = transactionHelper.inNewTransaction {
            workflowInstanceRepository.abandonNode(candidate, message, now).also { abandoned ->
                if (abandoned) {
                    workflowInstanceRepository.stopInstance(candidate.instanceId)
                }
            }
        }
        if (failed) {
            logger.warn(
                "Workflow node recovery: instance {}, node {} ({}, owner = {}) put in error: {}",
                candidate.instanceId,
                candidate.nodeId,
                candidate.status,
                candidate.owner,
                message,
            )
        }
    }

    private fun accountExists(account: String): Boolean =
        securityService.asAdmin {
            accountService.findAccountByName(account)
        } != null

    private fun asLauncher(launcher: WorkflowInstanceLauncher, code: () -> Unit) {
        val account = launcher.account
        if (account != null) {
            authenticationStorageService.withAccountId(account, launcher.actor, code)
        } else {
            securityService.asAdmin(code)
        }
    }
}
