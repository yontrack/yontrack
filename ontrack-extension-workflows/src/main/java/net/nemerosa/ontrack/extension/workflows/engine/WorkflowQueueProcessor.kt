package net.nemerosa.ontrack.extension.workflows.engine

import net.nemerosa.ontrack.extension.queue.QueueAckMode
import net.nemerosa.ontrack.extension.queue.QueueMetadata
import net.nemerosa.ontrack.extension.queue.QueueProcessor
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

@Component
class WorkflowQueueProcessor(
    private val applicationContext: ApplicationContext,
) : QueueProcessor<WorkflowQueuePayload> {

    private val workflowEngine: WorkflowEngine by lazy {
        applicationContext.getBean(WorkflowEngine::class.java)
    }

    override val id: String = "workflow"
    override val payloadType: KClass<WorkflowQueuePayload> = WorkflowQueuePayload::class

    /**
     * Minimum of 5 queues for the workflows.
     */
    override val defaultScale: Int = 5

    /**
     * Acks the messages once processed, so that a message in hand when the consumer stops is
     * redelivered. The processing of a message launches the node, whose execution goes on
     * outside the listener: the message is acked once the node is launched.
     */
    override val ackMode: QueueAckMode = QueueAckMode.END

    override fun isCancelled(payload: WorkflowQueuePayload): String? = null

    override fun process(payload: WorkflowQueuePayload, queueMetadata: QueueMetadata?) {
        workflowEngine.processNode(
            workflowInstanceId = payload.workflowInstanceId,
            workflowNodeId = payload.workflowNodeId,
        )
    }

    override fun getRoutingIdentifier(payload: WorkflowQueuePayload): String =
        "${payload.workflowInstanceId}-${payload.workflowNodeId}"

}