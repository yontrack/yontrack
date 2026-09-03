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

    override val executors: List<WorkflowNodeExecutor> by lazy {
        extensionManager.getExtensions(WorkflowNodeExecutor::class.java).sortedBy { it.displayName }
    }

    override fun findExecutor(executorId: String): WorkflowNodeExecutor? =
        executors.find { it.id == executorId }

    override fun getExecutor(executorId: String): WorkflowNodeExecutor =
        findExecutor(executorId)
            ?: throw WorkflowNodeExecutorNotFoundException(executorId)

    override fun validateWorkflowNodes(workflow: Workflow) {
        workflow.nodes.forEach { node ->
            validateWorkflowNode(workflow, node)
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
