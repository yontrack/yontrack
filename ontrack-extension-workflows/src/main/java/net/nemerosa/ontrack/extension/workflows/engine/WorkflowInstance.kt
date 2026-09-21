package net.nemerosa.ontrack.extension.workflows.engine

import com.fasterxml.jackson.annotation.JsonIgnore
import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.workflows.definition.Workflow
import net.nemerosa.ontrack.model.events.SerializableEvent
import net.nemerosa.ontrack.model.templating.TemplatingContextData
import net.nemerosa.ontrack.model.trigger.TriggerData
import java.time.Duration
import java.time.LocalDateTime

/**
 * Information about the execution of a workflow.
 *
 * @property id Unique ID for this workflow instance.
 * @property timestamp Last time the instance was updated.
 * @property workflow Associated workflow
 * @property event Serializable event linked to the workflow
 * @property triggerData Trigger for this workflow
 * @property contexts List of contexts linked to this instance
 * @property nodesExecutions Information about the node executions
 * @property status Status of the execution of this workflow
 */
data class WorkflowInstance(
    val id: String,
    val timestamp: LocalDateTime,
    val workflow: Workflow,
    val event: SerializableEvent,
    val triggerData: TriggerData? = null,
    val contexts: Map<String, TemplatingContextData>,
    val nodesExecutions: List<WorkflowInstanceNode>,
) {

    val status: WorkflowInstanceStatus = computeStatus(nodesExecutions)

    companion object {
        const val EVENT_INSTANCE_ID = "workflowInstanceId"

        /**
         * See [WorkflowInstanceRepository][net.nemerosa.ontrack.extension.workflows.repository.WorkflowInstanceRepository]
         */
        fun computeStatus(nodesExecutions: List<WorkflowInstanceNode>): WorkflowInstanceStatus {
            val nodes = nodesExecutions.map { it.status }
            return if (nodes.all { it == WorkflowInstanceNodeStatus.CREATED }) {
                WorkflowInstanceStatus.STARTED
            } else if (nodes.any { !it.finished }) {
                WorkflowInstanceStatus.RUNNING
            } else if (nodes.any { it == WorkflowInstanceNodeStatus.ERROR }) {
                WorkflowInstanceStatus.ERROR
            } else if (nodes.any { it == WorkflowInstanceNodeStatus.CANCELLED || it == WorkflowInstanceNodeStatus.TIMEOUT }) {
                WorkflowInstanceStatus.STOPPED
            } else if (nodes.all { it == WorkflowInstanceNodeStatus.SUCCESS }) {
                WorkflowInstanceStatus.SUCCESS
            } else {
                WorkflowInstanceStatus.RUNNING
            }
        }
    }

    private fun withTimestamp(timestamp: LocalDateTime) = WorkflowInstance(
        id = id,
        timestamp = timestamp,
        workflow = workflow,
        event = event,
        triggerData = triggerData,
        contexts = contexts,
        nodesExecutions = nodesExecutions,
    )

    fun truncateTimestamp() = withTimestamp(Time.truncate(timestamp))

    @get:JsonIgnore
    val startTime: LocalDateTime? by lazy {
        nodesExecutions.mapNotNull { it.startTime }.minOrNull()
    }

    @get:JsonIgnore
    val endTime: LocalDateTime? by lazy {
        nodesExecutions.mapNotNull { it.endTime }.maxOrNull()
    }

    @get:JsonIgnore
    val durationMs: Long by lazy {
        if (startTime != null && endTime != null) {
            Duration.between(startTime, endTime).toMillis()
        } else {
            0
        }
    }

    private fun updateContext(
        eventToMerge: SerializableEvent? = null,
    ) = WorkflowInstance(
        id = id,
        timestamp = Time.now,
        workflow = workflow,
        event = eventToMerge ?: event,
        triggerData = triggerData,
        contexts = contexts,
        nodesExecutions = nodesExecutions,
    )

    private fun updateNode(
        nodeId: String,
        update: (node: WorkflowInstanceNode) -> WorkflowInstanceNode
    ): WorkflowInstance {
        val nodesExecutions = nodesExecutions.map { node ->
            if (node.id == nodeId) {
                update(node)
            } else {
                node
            }
        }
        return WorkflowInstance(
            id = id,
            timestamp = Time.now,
            workflow = workflow,
            event = event,
            triggerData = triggerData,
            contexts = contexts,
            nodesExecutions = nodesExecutions,
        )
    }

    fun startNode(nodeId: String, time: LocalDateTime = Time.now) = updateNode(nodeId) { node ->
        node.start(time)
    }

    fun successNode(
        nodeId: String,
        output: JsonNode,
        eventToMerge: SerializableEvent? = null,
    ) = updateNode(nodeId) { node ->
        node.success(output)
    }.run {
        updateContext(eventToMerge)
    }

    fun getNode(nodeId: String) = nodesExecutions.firstOrNull { it.id == nodeId }
        ?: throw WorkflowNodeNotFoundException(nodeId)

}

