package net.nemerosa.ontrack.extension.workflows.execution

import net.nemerosa.ontrack.extension.api.ExtensionManager
import net.nemerosa.ontrack.extension.notifications.subscriptions.EventSubscriptionConfigException
import net.nemerosa.ontrack.extension.workflows.definition.Workflow
import net.nemerosa.ontrack.extension.workflows.definition.WorkflowNode
import net.nemerosa.ontrack.extension.workflows.definition.WorkflowValidationException
import org.springframework.stereotype.Service

@Service
class WorkflowNodeExecutorServiceImpl(
    private val extensionManager: ExtensionManager,
) : WorkflowNodeExecutorService {

    companion object {
        /**
         * Maximum number of workflow levels one validation may visit.
         *
         * A workflow nests another one when a node uses the `notification` executor on the `workflow`
         * channel, and that channel validates the workflow it carries by calling
         * [validateWorkflowNodes] back. Three levels leave room for the deepest nesting anybody has a
         * reason to write - a workflow, the workflow one of its notifications launches, and one more -
         * while capping the fan-out of a single validation request.
         */
        const val MAX_VALIDATION_DEPTH = 3
    }

    /**
     * Number of workflow levels the validation running on this thread has entered.
     *
     * The counter is held per thread rather than passed as a parameter because the nesting leaves this
     * service on the way down: it goes out through `NotificationChannel.validate(JsonNode)`, an
     * extension point implemented by every channel and knowing nothing of workflows, before coming back
     * here. Threading a depth through that API would push a workflow-only concern onto all sixteen
     * channels and every caller of theirs. One validation is a single synchronous call chain on one
     * thread, so a [ThreadLocal] sees exactly the same nesting a parameter would, and only
     * [validateWorkflowNodes] - which unwinds it in a `finally` - ever touches it.
     */
    private val validationDepth = ThreadLocal.withInitial { 0 }

    override val executors: List<WorkflowNodeExecutor> by lazy {
        extensionManager.getExtensions(WorkflowNodeExecutor::class.java).sortedBy { it.displayName }
    }

    override fun findExecutor(executorId: String): WorkflowNodeExecutor? =
        executors.find { it.id == executorId }

    override fun getExecutor(executorId: String): WorkflowNodeExecutor =
        findExecutor(executorId)
            ?: throw WorkflowNodeExecutorNotFoundException(executorId)

    override fun validateWorkflowNodes(workflow: Workflow) {
        val depth = validationDepth.get() + 1
        if (depth > MAX_VALIDATION_DEPTH) {
            // Reported the same way as any other rejection of the workflow's content, so that the
            // preview of the edition dialog displays it and a save refuses the workflow with a 400,
            // instead of the whole request dying of exhaustion somewhere down the recursion.
            throw WorkflowValidationException(
                name = workflow.name,
                message = "Workflows cannot be nested more than $MAX_VALIDATION_DEPTH levels deep",
            )
        }
        validationDepth.set(depth)
        try {
            workflow.nodes.forEach { node ->
                validateWorkflowNode(workflow, node)
            }
        } finally {
            if (depth > 1) {
                validationDepth.set(depth - 1)
            } else {
                validationDepth.remove()
            }
        }
    }

    private fun validateWorkflowNode(workflow: Workflow, node: WorkflowNode) {
        val executor = findExecutor(node.executorId)
            ?: throw WorkflowValidationException(
                name = workflow.name,
                message = """Workflow node executor ID "${node.executorId}" not found"""
            )
        try {
            executor.validate(node.data)
        } catch (ex: EventSubscriptionConfigException) {
            // Only the notification executor reports its errors this way, so naming the notification
            // here is accurate for every case which reaches this branch.
            throw EventSubscriptionConfigException(
                innerMessage = """
                    Configuration for the notification in node "${node.id}" is not valid > ${ex.innerMessage}
                """.trimIndent(),
            )
        }
    }

}
