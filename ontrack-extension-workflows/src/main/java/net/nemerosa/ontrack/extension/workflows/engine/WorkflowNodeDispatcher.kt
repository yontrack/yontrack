package net.nemerosa.ontrack.extension.workflows.engine

import net.nemerosa.ontrack.extension.queue.dispatching.QueueDispatcher
import net.nemerosa.ontrack.extension.queue.source.createQueueSource
import org.springframework.stereotype.Component

/**
 * Sends a workflow node to the queue, for a process to run it.
 *
 * The message carries the identity of the current authentication.
 */
@Component
class WorkflowNodeDispatcher(
    private val queueDispatcher: QueueDispatcher,
    private val workflowQueueProcessor: WorkflowQueueProcessor,
    private val workflowQueueSourceExtension: WorkflowQueueSourceExtension,
) {

    fun dispatch(workflowInstanceId: String, workflowNodeId: String) {
        queueDispatcher.dispatch(
            queueProcessor = workflowQueueProcessor,
            payload = WorkflowQueuePayload(
                workflowInstanceId = workflowInstanceId,
                workflowNodeId = workflowNodeId,
            ),
            source = workflowQueueSourceExtension.createQueueSource(
                WorkflowQueueSourceData(
                    workflowInstanceId = workflowInstanceId,
                    workflowNodeId = workflowNodeId,
                )
            )
        )
    }
}
