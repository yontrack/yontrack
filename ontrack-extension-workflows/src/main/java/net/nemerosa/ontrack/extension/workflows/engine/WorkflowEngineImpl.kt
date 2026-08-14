package net.nemerosa.ontrack.extension.workflows.engine

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import net.nemerosa.ontrack.extension.queue.dispatching.QueueDispatcher
import net.nemerosa.ontrack.extension.queue.source.createQueueSource
import net.nemerosa.ontrack.extension.workflows.WorkflowConfigurationProperties
import net.nemerosa.ontrack.extension.workflows.definition.Workflow
import net.nemerosa.ontrack.extension.workflows.definition.WorkflowNode
import net.nemerosa.ontrack.extension.workflows.definition.WorkflowParentNode
import net.nemerosa.ontrack.extension.workflows.definition.WorkflowValidation
import net.nemerosa.ontrack.extension.workflows.definition.totalTimeout
import net.nemerosa.ontrack.extension.workflows.execution.WorkflowNodeExecutorResultType
import net.nemerosa.ontrack.extension.workflows.execution.WorkflowNodeExecutorService
import net.nemerosa.ontrack.extension.workflows.repository.WorkflowInstanceRepository
import net.nemerosa.ontrack.model.events.SerializableEvent
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.templating.TemplatingContextData
import net.nemerosa.ontrack.model.trigger.TriggerData
import net.nemerosa.ontrack.model.tx.DefaultTransactionHelper
import net.nemerosa.ontrack.model.tx.TransactionRetry
import net.nemerosa.ontrack.model.utils.launchAsyncWithSecurityContext
import net.nemerosa.ontrack.model.utils.launchWithSecurityContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import java.time.Duration

@Component
class WorkflowEngineImpl(
    private val workflowInstanceRepository: WorkflowInstanceRepository,
    private val queueDispatcher: QueueDispatcher,
    private val workflowQueueProcessor: WorkflowQueueProcessor,
    private val workflowQueueSourceExtension: WorkflowQueueSourceExtension,
    private val workflowNodeExecutorService: WorkflowNodeExecutorService,
    private val workflowConfigurationProperties: WorkflowConfigurationProperties,
    private val securityService: SecurityService,
    platformTransactionManager: PlatformTransactionManager,
    private val transactionRetry: TransactionRetry,
) : WorkflowEngine {

    private val logger: Logger = LoggerFactory.getLogger(WorkflowEngineImpl::class.java)

    // Nodes run on their own threads, outside of any ambient transaction, so real new transactions
    // are always needed here - even in test mode where the TransactionHelper bean is a pass-through.
    private val transactionHelper = DefaultTransactionHelper(platformTransactionManager, transactionRetry)

    override fun startWorkflow(
        workflow: Workflow,
        event: SerializableEvent,
        triggerData: TriggerData,
        contexts: Map<String, TemplatingContextData>,
        pauseMs: Long,
    ): WorkflowInstance {
        // Checks the workflow consistency (cycles, etc.) - use a public method, usable by extensions
        WorkflowValidation.validateWorkflow(workflow).throwErrorIfAny()

        // Adapting the workflow with additional nodes
        var actualWorkflow = workflow
        // Pause node
        if (pauseMs > 0) {
            actualWorkflow = actualWorkflow.addNodeBeforeEach(pauseNode(pauseMs))
        }
        // TODO Termination node

        // Creating the instance
        val instance = createInstance(
            workflow = actualWorkflow,
            event = event,
            triggerData = triggerData,
            contexts = contexts,
        )

        // Storing the instance
        transactionHelper.inNewTransaction {
            workflowInstanceRepository.createInstance(instance)
        }

        for (node in instance.workflow.nodes) {
            queueDispatcher.dispatch(
                queueProcessor = workflowQueueProcessor,
                payload = WorkflowQueuePayload(
                    workflowInstanceId = instance.id,
                    workflowNodeId = node.id,
                ),
                source = workflowQueueSourceExtension.createQueueSource(
                    WorkflowQueueSourceData(
                        workflowInstanceId = instance.id,
                        workflowNodeId = node.id,
                    )
                )
            )
        }

        // OK
        return instance
    }

    private fun debug(message: String, instance: WorkflowInstance, nodeId: String) {
        logger.debug(
            // WORKFLOW <instance> <name> <node> <user> <message>
            "WORKFLOW [{}] [{}] [{}] [{}] [{}]",
            instance.id,
            instance.workflow.name,
            nodeId,
            SecurityContextHolder.getContext().authentication?.name,
            message
        )
    }

    private fun debugParent(message: String, instance: WorkflowInstance, nodeId: String) {
//        logger.debug(
//            // WORKFLOW <instance> <name> <node> <message>
//            "WORKFLOW [{}] [{}] [{}] [{}]",
//            instance.id,
//            instance.workflow.name,
//            nodeId,
//            message
//        )
    }

    private fun error(message: String, instance: WorkflowInstance, nodeId: String, error: Throwable? = null) {
        val logMessage =
            "[${SecurityContextHolder.getContext().authentication?.name}] WORKFLOW ${instance.workflow.name} INSTANCE ${instance.id} NODE $nodeId: $message"
        if (error != null) {
            logger.error(logMessage, error)
        } else {
            logger.error(logMessage)
        }
    }

    override fun processNode(workflowInstanceId: String, workflowNodeId: String) {
        // Call must be authenticated
        checkAuthentication("Workflow node received not authenticated")
        // Getting the instance
        val instance = getWorkflowInstanceTx(workflowInstanceId)
        val node = instance.workflow.getNode(workflowNodeId)
        val nodeExecution = instance.getNode(workflowNodeId)
        debug("NODE RECEIVED", instance, workflowNodeId)
        // Checks of the instance has been interrupted or not
        if (instance.status.finished) {
            debug("NODE INSTANCE FINISHED", instance, workflowNodeId)
            return
        }
        // Checks the instance node status
        if (nodeExecution.status.finished) {
            debug("NODE FINISHED", instance, workflowNodeId)
            return
        } else if (nodeExecution.status != WorkflowInstanceNodeStatus.CREATED) {
            error("NODE NOT IN CREATED STATE", instance, workflowNodeId)
            return
        }
        // Starting the job for the node
        launchWithSecurityContext {
            try {
                runNode(instance, workflowInstanceId, workflowNodeId, node)
            } catch (cancellation: CancellationException) {
                // Cancellation is not a node failure - letting the coroutine machinery deal with it
                throw cancellation
            } catch (any: Throwable) {
                // Nothing must ever escape this coroutine: an uncaught error here would leave the
                // node in a non-terminal state forever (and the instance running forever).
                error("NODE UNCAUGHT ERROR", instance, workflowNodeId, any)
                nodeErrorSafe(instance, workflowInstanceId, workflowNodeId, any)
            }
        }
        debug("NODE LAUNCHED", instance, workflowNodeId)
    }

    /**
     * Actual processing of a node, once it has been accepted by [processNode].
     *
     * Extracted from the coroutine body so that the failure paths can be unit tested.
     */
    private suspend fun runNode(
        instance: WorkflowInstance,
        workflowInstanceId: String,
        workflowNodeId: String,
        node: WorkflowNode,
    ) {
        // Call must be authenticated
        checkAuthentication("Workflow node process not authenticated")
        debug("NODE PROCESSING", instance, workflowNodeId)
        // Changes the node's status to WAITING
        debug("NODE WAITING", instance, workflowNodeId)
        nodeWaiting(workflowInstanceId, workflowNodeId)
        // Waiting for the parent nodes to be OK
        val okToStart = if (node.parents.isNotEmpty()) {
            debug("NODE WAITING FOR PARENTS", instance, workflowNodeId)
            val parentStatuses = awaitParents(instance, workflowNodeId, node.parents)
            debug("NODE WAITED FOR PARENTS", instance, workflowNodeId)
            // TODO OK to start depending on the conditions
            val parentsOK = parentStatuses.all { it.status == WorkflowInstanceNodeStatus.SUCCESS }
            // Checking the instance state again
            if (parentsOK) {
                !getWorkflowInstanceTx(workflowInstanceId).status.finished
            } else {
                val statusSummary = parentStatuses.joinToString(",") {
                    "${it.parentDef.id}=${it.status}"
                }
                debugParent("NODE PARENTS STATUSES: $statusSummary", instance, workflowNodeId)
                false
            }
        } else {
            // No parent
            true
        }
        // Starting the node execution
        if (okToStart) {
            debug("NODE STARTED", instance, workflowNodeId)
            nodeStarted(workflowInstanceId, workflowNodeId)
            // Loading a fresh instance before starting
            val freshInstance = getWorkflowInstanceTx(workflowInstanceId)
            // Starts the node execution
            nodeExecution(freshInstance, workflowNodeId)
        } else {
            debug("NODE PARENT NOT OK", instance, workflowNodeId)
            nodeCancelled(workflowInstanceId, workflowNodeId, "Parents conditions were not met.")
        }
    }

    private fun getWorkflowInstanceTx(workflowInstanceId: String) = transactionHelper.inNewTransaction {
        workflowInstanceRepository.findWorkflowInstance(workflowInstanceId)
            ?: error("Could not find the workflow instance: $workflowInstanceId")
    }

    /**
     * Waits for all the [parents] of a node to reach a final status.
     *
     * A single polling loop, using a single batched query per iteration, is used for all the parents:
     * one coroutine and one query per parent would multiply the load on the connection pool by the
     * number of parents, for the whole duration of the parents' execution.
     *
     * Each parent keeps its own deadline, based on its total timeout. A parent which has not
     * finished by then is reported as [WorkflowInstanceNodeStatus.TIMEOUT].
     */
    private suspend fun awaitParents(
        instance: WorkflowInstance,
        workflowNodeId: String,
        parents: List<WorkflowParentNode>,
    ): List<WorkflowParentStatus> {
        // The Spring security context is held in a thread local while a coroutine can resume on any
        // thread of its dispatcher after a `delay`. It must therefore be restored at every resumption,
        // and on the way out, or the rest of the node processing would run unauthenticated.
        val securityContext = SecurityContextHolder.getContext()
        try {
            val start = System.currentTimeMillis()
            // Deadline of each parent, based on its own total timeout
            val deadlines = parents.associate { parentDef ->
                val parentNode = instance.workflow.getNode(parentDef.id)
                parentDef.id to start + parentNode.totalTimeout(instance.workflow) * 1_000L
            }
            val finalStatuses = mutableMapOf<String, WorkflowInstanceNodeStatus>()
            val pending = parents.map { it.id }.toMutableSet()
            while (pending.isNotEmpty()) {
                debugParent("NODE PARENTS WAIT ${pending.joinToString(",")} STATUS", instance, workflowNodeId)
                // One query for all the parents which are still pending
                val statuses = getNodeStatuses(instance.id, pending.toList())
                val now = System.currentTimeMillis()
                val iterator = pending.iterator()
                while (iterator.hasNext()) {
                    val parentId = iterator.next()
                    val status = statuses[parentId]
                    when {
                        status != null && status.finished -> {
                            finalStatuses[parentId] = status
                            iterator.remove()
                        }
                        // A missing row or a still-running parent both expire on the deadline
                        now >= (deadlines[parentId] ?: now) -> {
                            debugParent("NODE PARENT WAIT $parentId TIMEOUT", instance, workflowNodeId)
                            finalStatuses[parentId] = WorkflowInstanceNodeStatus.TIMEOUT
                            iterator.remove()
                        }
                    }
                }
                if (pending.isNotEmpty()) {
                    debugParent("NODE PARENTS WAIT ${pending.joinToString(",")} DELAY", instance, workflowNodeId)
                    delay(workflowConfigurationProperties.parentWaitingInterval.toMillis())
                    SecurityContextHolder.setContext(securityContext)
                }
            }
            return parents.map { parentDef ->
                WorkflowParentStatus(parentDef, finalStatuses[parentDef.id] ?: WorkflowInstanceNodeStatus.TIMEOUT)
            }
        } finally {
            SecurityContextHolder.setContext(securityContext)
        }
    }

    private suspend fun nodeExecution(instance: WorkflowInstance, nodeId: String) {
        // Getting the node
        val node = instance.workflow.getNode(nodeId)
        // Getting the node executor
        val executor = workflowNodeExecutorService.getExecutor(node.executorId)
        // Checking if the executor is enabled
        if (!executor.enabled) {
            throw WorkflowNodeExecutorNotEnabledException(executor)
        }
        // Timeout
        val timeout = Duration.ofSeconds(node.timeout)

        // Continuous feedback for the node
        val nodeFeedback: (output: JsonNode?) -> Unit = { output: JsonNode? ->
            if (output != null) {
                nodeProgress(instance.id, nodeId, output)
            }
        }

        try {

            // Running the executor
            val result = withTimeoutOrNull(timeout.toMillis()) {
                val deferred = launchAsyncWithSecurityContext(context = coroutineContext) {
                    debug("NODE EXECUTING", instance, nodeId)
                    // Call must be authenticated
                    checkAuthentication("Workflow node execution not authenticated")
                    // Running the execution
                    executor.execute(instance, node.id, nodeFeedback)
                }
                val outcome = deferred.await()
                debug("NODE EXECUTED (type = ${outcome.type})", instance, nodeId)
                outcome
            }

            // Timeout?
            if (result == null) {
                debug("NODE TIMEOUT", instance, nodeId)
                nodeError(instance.id, nodeId, "Timeout", null)
            }
            // Progressing the instance or stopping it in case of error
            else {
                when (result.type) {
                    WorkflowNodeExecutorResultType.ERROR -> {
                        debug("NODE ERROR (message = ${result.message})", instance, nodeId)
                        nodeError(instance.id, nodeId, result.message, result.output)
                    }

                    WorkflowNodeExecutorResultType.SUCCESS -> {
                        debug("NODE SUCCESS", instance, nodeId)
                        // Stores the output back into the instance and progresses the node's status
                        nodeSuccess(instance.id, nodeId, result.output, result.event)
                    }
                }
            }
        } catch (cancellation: CancellationException) {
            // Cancellation is not a node failure
            throw cancellation
        } catch (any: Throwable) {
            // Stores the node error status
            error("NODE UNCAUGHT ERROR", instance, nodeId, any)
            nodeError(instance.id, nodeId, any.errorMessage(), null)
        }
    }

    private fun checkAuthentication(message: String) {
        securityService.currentUser ?: error(message)
    }

    /**
     * A single read does not need its own transaction: going through the transaction helper here
     * would hold a pooled connection for a whole `REQUIRES_NEW` transaction on every polling tick.
     * The retry is still needed though - this is the read which fails first when the pool is starved.
     */
    private fun getNodeStatuses(
        instanceId: String,
        nodeIds: Collection<String>,
    ): Map<String, WorkflowInstanceNodeStatus> =
        transactionRetry.withRetry {
            workflowInstanceRepository.getNodeStatuses(instanceId, nodeIds)
        }

    private fun nodeWaiting(workflowInstanceId: String, workflowNodeId: String) {
        transactionHelper.inNewTransaction {
            workflowInstanceRepository.nodeWaiting(workflowInstanceId, workflowNodeId)
        }
    }

    private fun nodeStarted(workflowInstanceId: String, workflowNodeId: String) {
        transactionHelper.inNewTransaction {
            workflowInstanceRepository.nodeStarted(workflowInstanceId, workflowNodeId)
        }
    }

    private fun nodeCancelled(
        workflowInstanceId: String,
        workflowNodeId: String,
        @Suppress("SameParameterValue") message: String
    ) {
        transactionHelper.inNewTransaction {
            workflowInstanceRepository.nodeCancelled(workflowInstanceId, workflowNodeId, message)
        }
    }

    private fun nodeProgress(workflowInstanceId: String, workflowNodeId: String, output: JsonNode) {
        transactionHelper.inNewTransaction {
            workflowInstanceRepository.nodeProgress(workflowInstanceId, workflowNodeId, output)
        }
    }

    private fun nodeSuccess(
        workflowInstanceId: String,
        workflowNodeId: String,
        output: JsonNode?,
        event: SerializableEvent?
    ) {
        transactionHelper.inNewTransaction {
            workflowInstanceRepository.nodeSuccess(workflowInstanceId, workflowNodeId, output, event)
        }
    }

    private fun nodeError(instanceId: String, nodeId: String, message: String?, output: JsonNode?) {
        transactionHelper.inNewTransaction {
            workflowInstanceRepository.nodeError(instanceId, nodeId, message, output)
            workflowInstanceRepository.stopInstance(instanceId)
        }
    }

    /**
     * Marks a node in error, without ever throwing.
     *
     * This is the last resort when the node processing failed: if the database is still unavailable
     * after the retries, there is nothing left to do but log it loudly - throwing here would put us
     * back into the very situation we are guarding against.
     */
    private fun nodeErrorSafe(
        instance: WorkflowInstance,
        instanceId: String,
        nodeId: String,
        failure: Throwable,
    ) {
        try {
            nodeError(instanceId, nodeId, failure.errorMessage(), null)
        } catch (secondary: Throwable) {
            error("NODE ERROR STATUS COULD NOT BE SAVED", instance, nodeId, secondary)
        }
    }

    /**
     * Message to store for a failure. Some exceptions (NPE and the like) carry no message at all,
     * and the node error must never end up being recorded as `null`.
     */
    private fun Throwable.errorMessage(): String = message ?: this::class.java.name

    override fun findWorkflowInstance(id: String): WorkflowInstance? =
        transactionHelper.inNewTransactionNullable {
            workflowInstanceRepository.findWorkflowInstance(id)
        }

    override fun stopWorkflow(workflowInstanceId: String) {
        transactionHelper.inNewTransaction {
            workflowInstanceRepository.stopInstance(workflowInstanceId)
        }
    }
}