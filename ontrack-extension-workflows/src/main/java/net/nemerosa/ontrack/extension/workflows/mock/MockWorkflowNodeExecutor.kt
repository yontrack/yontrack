package net.nemerosa.ontrack.extension.workflows.mock

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.extension.support.AbstractExtension
import net.nemerosa.ontrack.extension.workflows.WorkflowsExtensionFeature
import net.nemerosa.ontrack.extension.workflows.engine.WorkflowInstance
import net.nemerosa.ontrack.extension.workflows.execution.WorkflowNodeExecutor
import net.nemerosa.ontrack.extension.workflows.execution.WorkflowNodeExecutorConfigException
import net.nemerosa.ontrack.extension.workflows.execution.WorkflowNodeExecutorResult
import net.nemerosa.ontrack.extension.workflows.templating.WorkflowTemplatingContext
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.docs.Documentation
import net.nemerosa.ontrack.model.docs.DocumentationExampleCode
import net.nemerosa.ontrack.model.events.EventTemplatingService
import net.nemerosa.ontrack.model.events.PlainEventRenderer
import net.nemerosa.ontrack.model.events.SerializableEventService
import net.nemerosa.ontrack.model.templating.TemplatingService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
@ConditionalOnProperty(
    prefix = "ontrack.config.extension.workflows.mock",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
@APIDescription(
    "Simulates a step with a configurable outcome, without performing any real action. " +
            "Intended for demonstrations and testing - do not enable on an instance tracking real deliveries."
)
@Documentation(MockNodeData::class)
@Documentation(MockNodeOutput::class, section = "output")
@DocumentationExampleCode(
    """
    executorId: mock
    data:
        text: Some text to store
        waitMs: 500
        error: false
"""
)
class MockWorkflowNodeExecutor(
    workflowsExtensionFeature: WorkflowsExtensionFeature,
    private val eventTemplatingService: EventTemplatingService,
    private val serializableEventService: SerializableEventService,
    private val templatingService: TemplatingService,
) : AbstractExtension(workflowsExtensionFeature), WorkflowNodeExecutor {

    companion object {
        const val EVENT_MOCK = "mock"

        /**
         * Ceiling for [MockNodeData.waitMs]. The wait is a blocking one, occupying the workflow
         * executor thread, so an unbounded value would let anyone able to author a workflow park
         * those threads for as long as they like. The highest wait used anywhere in the tests and
         * in the demo seed is 2s, so this leaves ample room.
         */
        const val MAX_WAIT_MS = 60_000L

        fun clampWaitMs(waitMs: Long): Long = waitMs.coerceIn(0L, MAX_WAIT_MS)
    }

    override val id: String = "mock"
    override val displayName: String = "Simulated gate"

    private val texts = ConcurrentHashMap<String, List<String>>()

    fun getTextsByInstanceId(instanceId: String): List<String> = texts[instanceId] ?: emptyList()

    private fun parseData(data: JsonNode): MockNodeData =
        if (data.isTextual) {
            MockNodeData(data.asText())
        } else {
            data.parse<MockNodeData>()
        }

    override fun validate(data: JsonNode) {
        val parsed = parseData(data)
        if (parsed.text.isBlank()) {
            throw WorkflowNodeExecutorConfigException("Text is required for mock node executor")
        }
    }

    override fun execute(
        workflowInstance: WorkflowInstance,
        workflowNodeId: String,
        workflowNodeExecutorResultFeedback: (output: JsonNode?) -> Unit,
    ): WorkflowNodeExecutorResult {
        // Gets the node & its data
        val nodeRawData = workflowInstance.workflow.getNode(workflowNodeId).data
        val nodeData = parseData(nodeRawData)
        // Error?
        if (nodeData.error) {
            error("Error in $workflowNodeId node")
        }
        // Waiting time
        val waitMs = clampWaitMs(nodeData.waitMs)
        if (waitMs > 0) {
            runBlocking {
                delay(waitMs)
            }
        }

        // Using the event context
        val context = workflowInstance.event.findValue(EVENT_MOCK)

        // Initial text
        val initialText = nodeData.text

        // Templating
        val replacedText = if (templatingService.isTemplate(initialText)) {
            val templatingEvent = serializableEventService.hydrate(workflowInstance.event)
            val additionalContext = WorkflowTemplatingContext.createTemplatingContext(workflowInstance)
            eventTemplatingService.renderEvent(
                event = templatingEvent,
                template = initialText,
                renderer = PlainEventRenderer.INSTANCE,
                context = additionalContext,
            )
        } else {
            initialText
        }

        // Returning some new text
        val text = "Processed: $replacedText for $context"

        // Recording
        texts.compute(workflowInstance.id) { _, old ->
            if (old != null) old + text else listOf(text)
        }
        // OK
        return WorkflowNodeExecutorResult.success(
            MockNodeOutput(
                text = text
            ).asJson()
        )
    }
}