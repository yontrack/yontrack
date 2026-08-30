package net.nemerosa.ontrack.extension.workflows.notifications

import com.fasterxml.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.extension.notifications.channels.AbstractNotificationChannel
import net.nemerosa.ontrack.extension.notifications.channels.NoTemplate
import net.nemerosa.ontrack.extension.notifications.channels.NotificationResult
import net.nemerosa.ontrack.extension.notifications.recording.NotificationRecord
import net.nemerosa.ontrack.extension.notifications.subscriptions.EventSubscriptionConfigException
import net.nemerosa.ontrack.extension.notifications.trigger.NotificationRecordTrigger
import net.nemerosa.ontrack.extension.notifications.trigger.NotificationRecordTriggerData
import net.nemerosa.ontrack.extension.workflows.definition.Workflow
import net.nemerosa.ontrack.extension.workflows.definition.WorkflowNode
import net.nemerosa.ontrack.extension.workflows.definition.WorkflowValidation
import net.nemerosa.ontrack.extension.workflows.definition.WorkflowValidationException
import net.nemerosa.ontrack.extension.workflows.engine.WorkflowEngine
import net.nemerosa.ontrack.extension.workflows.engine.WorkflowInstanceStatus
import net.nemerosa.ontrack.extension.workflows.execution.WorkflowNodeExecutorService
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.getTextField
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.docs.Documentation
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventTemplatingService
import net.nemerosa.ontrack.model.events.PlainEventRenderer
import net.nemerosa.ontrack.model.events.SerializableEventService
import net.nemerosa.ontrack.model.trigger.createTriggerData
import org.springframework.stereotype.Component

@Component
@APIDescription("Launches a workflow")
@NoTemplate
@Documentation(WorkflowNotificationChannelConfig::class)
@Documentation(WorkflowNotificationChannelOutput::class, section = "output")
class WorkflowNotificationChannel(
    private val workflowEngine: WorkflowEngine,
    private val eventTemplatingService: EventTemplatingService,
    private val workflowNodeExecutorService: WorkflowNodeExecutorService,
    private val serializableEventService: SerializableEventService,
    private val notificationRecordTrigger: NotificationRecordTrigger,
) : AbstractNotificationChannel<WorkflowNotificationChannelConfig, WorkflowNotificationChannelOutput>(
    WorkflowNotificationChannelConfig::class
) {

    override fun validateParsedConfig(config: WorkflowNotificationChannelConfig) {
        // Basic controls
        WorkflowValidation.validateWorkflow(config.workflow).throwErrorIfAny()
        // Controlling each node
        config.workflow.nodes.forEach { node ->
            validationWorkflowNode(config.workflow, node)
        }
    }

    override fun mergeConfig(
        a: WorkflowNotificationChannelConfig,
        changes: JsonNode
    ): WorkflowNotificationChannelConfig = changes.parse()

    private fun validationWorkflowNode(workflow: Workflow, node: WorkflowNode) {
        val executor = workflowNodeExecutorService.findExecutor(node.executorId)
            ?: throw WorkflowValidationException(
                name = workflow.name,
                message = """Workflow node executor ID "${node.executorId}" not found"""
            )
        try {
            executor.validate(node.data)
        } catch (ex: EventSubscriptionConfigException) {
            throw EventSubscriptionConfigException(
                innerMessage = """
                    Configuration for the notification in node "${node.id}" is not valid > ${ex.innerMessage}
                """.trimIndent(),
            )
        }
    }

    override fun publish(
        recordId: String,
        config: WorkflowNotificationChannelConfig,
        event: Event,
        context: Map<String, Any>,
        template: String?, // Not used for this notification channel
        outputProgressCallback: (current: WorkflowNotificationChannelOutput) -> WorkflowNotificationChannelOutput
    ): NotificationResult<WorkflowNotificationChannelOutput> {
        // Templating for the workflow name
        val workflow = config.workflow.rename {
            eventTemplatingService.renderEvent(
                event = event,
                context = context,
                template = it,
                renderer = PlainEventRenderer.INSTANCE,
            )
        }
        // Completing the event for the workflow
        val workflowEvent = serializableEventService.dehydrate(event)
            .withValue(
                WorkflowNotificationChannelNotificationRecord.CONTEXT_NOTIFICATION_RECORD_ID,
                recordId
            )
        // Launching the workflow (with the event as context, template is not used)
        val instance = workflowEngine.startWorkflow(
            workflow = workflow,
            event = workflowEvent,
            triggerData = notificationRecordTrigger.createTriggerData(
                NotificationRecordTriggerData(
                    recordId = recordId,
                )
            ),
            pauseMs = config.pauseMs,
        )
        // Output = just an ID to the workflow instance
        return NotificationResult.async(
            WorkflowNotificationChannelOutput(
                workflowInstanceId = instance.id,
            )
        )
    }

    override fun getNotificationResult(notificationRecord: NotificationRecord): NotificationResult<WorkflowNotificationChannelOutput>? {
        // Getting the workflow instance ID
        val instanceId =
            notificationRecord.result.output?.getTextField(WorkflowNotificationChannelOutput::workflowInstanceId.name)
                ?: return null
        // Loading the instance
        val instance = workflowEngine.findWorkflowInstance(instanceId) ?: return null
        // Getting result
        val output = WorkflowNotificationChannelOutput(workflowInstanceId = instance.id)
        return when (instance.status) {

            WorkflowInstanceStatus.STARTED -> NotificationResult.async(output)

            WorkflowInstanceStatus.RUNNING -> NotificationResult.ongoing(output)

            WorkflowInstanceStatus.STOPPED -> NotificationResult.error(
                message = "Workflow has been stopped",
                output = output,
            )

            WorkflowInstanceStatus.ERROR -> NotificationResult.error(
                message = "Workflow in error",
                output = output,
            )

            WorkflowInstanceStatus.SUCCESS -> NotificationResult.ok(output)
        }
    }

    override fun toSearchCriteria(text: String): JsonNode = mapOf(
        "workflow" to mapOf(
            "name" to text
        )
    ).asJson()

    override val type: String = TYPE
    override val displayName: String = "Workflow"
    override val enabled: Boolean = true

    companion object {
        /**
         * ID of the workflow notification channel.
         */
        const val TYPE = "workflow"
    }

}