package net.nemerosa.ontrack.extension.workflows.notifications

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.common.waitFor
import net.nemerosa.ontrack.extension.notifications.channels.*
import net.nemerosa.ontrack.extension.notifications.model.Notification
import net.nemerosa.ontrack.extension.notifications.model.createData
import net.nemerosa.ontrack.extension.notifications.processing.NotificationProcessingResult
import net.nemerosa.ontrack.extension.notifications.processing.NotificationProcessingResultService
import net.nemerosa.ontrack.extension.notifications.processing.NotificationProcessingService
import net.nemerosa.ontrack.extension.support.AbstractExtension
import net.nemerosa.ontrack.extension.workflows.WorkflowConfigurationProperties
import net.nemerosa.ontrack.extension.workflows.WorkflowsExtensionFeature
import net.nemerosa.ontrack.extension.workflows.engine.WorkflowInstance
import net.nemerosa.ontrack.extension.workflows.execution.WorkflowNodeExecutor
import net.nemerosa.ontrack.extension.workflows.execution.WorkflowNodeExecutorResult
import net.nemerosa.ontrack.extension.workflows.templating.WorkflowTemplatingContext
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.docs.Documentation
import net.nemerosa.ontrack.model.docs.DocumentationExampleCode
import net.nemerosa.ontrack.model.events.SerializableEventService
import net.nemerosa.ontrack.model.tx.TransactionHelper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toKotlinDuration

@Component
@APIDescription(
    """
    Wraps a notification in a workflow node.
    
    The output of this execution is exactly the output
    of the notification channel.
"""
)
@DocumentationExampleCode(
    """
        executorId: notification
        data:
            channel: slack
            channelConfig:
                channel: "#my-channel"
            template: |
                Message template
    """
)
@Documentation(WorkflowNotificationChannelNodeData::class)
class WorkflowNotificationChannelNodeExecutor(
    workflowsExtensionFeature: WorkflowsExtensionFeature,
    private val notificationProcessingService: NotificationProcessingService,
    private val notificationChannelRegistry: NotificationChannelRegistry,
    private val workflowNotificationSource: WorkflowNotificationSource,
    private val serializableEventService: SerializableEventService,
    private val notificationProcessingResultService: NotificationProcessingResultService,
    private val workflowConfigurationProperties: WorkflowConfigurationProperties,
    private val transactionHelper: TransactionHelper,
) : AbstractExtension(workflowsExtensionFeature), WorkflowNodeExecutor {

    private val logger: Logger = LoggerFactory.getLogger(WorkflowNotificationChannelNodeExecutor::class.java)

    companion object {
        const val ID: String = "notification"
    }

    override val id: String = ID
    override val displayName: String = "Notification"

    override fun validate(data: JsonNode) {
        val (channelType, channelConfig) = data.parse<WorkflowNotificationChannelNodeData>()
        val channel = notificationChannelRegistry.getChannel(channelType)
        val validation = channel.validate(channelConfig)
        if (!validation.isOk()) {
            validation.throwException()
        }
    }

    override fun execute(
        workflowInstance: WorkflowInstance,
        workflowNodeId: String,
        workflowNodeExecutorResultFeedback: (output: JsonNode?) -> Unit,
    ): WorkflowNodeExecutorResult {
        // Gets the node's data
        val node = workflowInstance.workflow.getNode(workflowNodeId)
        val (channel, channelConfig, template) = node.data.parse<WorkflowNotificationChannelNodeData>()
        // Creating the notification item
        val notification = Notification(
            source = workflowNotificationSource.createData(
                WorkflowNotificationSourceDataType(
                    workflowInstanceId = workflowInstance.id,
                )
            ),
            channel = channel,
            channelConfig = channelConfig,
            event = serializableEventService.hydrate(workflowInstance.event),
            template = template,
        )
        // Enriches the context
        val context = WorkflowTemplatingContext.createTemplatingContext(workflowInstance)
        // Feedback
        val outputFeedback = { recordId: String, output: Any? ->
            workflowNodeExecutorResultFeedback(
                WorkflowNotificationChannelNodeExecutorOutput(
                    recordId = recordId,
                    result = output?.asJson(),
                ).asJson()
            )
        }

        // Processing
        val processingResult = notificationProcessingService.process(notification, context, outputFeedback)
        val result = processingResult?.result

        // If running asynchronously, the result is not available yet,
        // and we need to wait for it

        return if (processingResult != null && result?.type == NotificationResultType.ASYNC) {
            val timeoutSeconds = node.timeout
            val interval = node.interval
                ?.let { Duration.ofSeconds(it) }
                ?: workflowConfigurationProperties.asyncCheckInterval
            val finalResult = waitFor(
                message = "Waiting for notification ${processingResult.recordId} to be processed",
                interval = interval.toKotlinDuration(),
                timeout = timeoutSeconds.seconds,
            ) {
                transactionHelper.inNewTransactionNullable {
                    notificationProcessingResultService.getActualizedResult(processingResult)
                }
            } until { actualResult ->
                logger.info("Checking notification result for ${processingResult.recordId}: ${actualResult.result?.type}")
                val actualResultType = actualResult.result?.type
                actualResultType != null && !actualResultType.running
            }
            val finalResultResult = finalResult.result
            finalResultResult.toWorkflowNodeExecutorResult(finalResult)
        } else if (processingResult != null && result != null) {
            result.toWorkflowNodeExecutorResult(processingResult)
        } else {
            WorkflowNodeExecutorResult.error("Notification did not return any result", null)
        }
    }

    private fun NotificationResult<out Any?>?.toWorkflowNodeExecutorResult(
        processingResult: NotificationProcessingResult<*>
    ): WorkflowNodeExecutorResult =
        if (this == null) {
            WorkflowNodeExecutorResult.error("Notification did not return any result", null)
        } else when (type) {

            NotificationResultType.OK -> WorkflowNotificationChannelNodeExecutorOutput.success(processingResult)

            NotificationResultType.ONGOING -> WorkflowNotificationChannelNodeExecutorOutput.error(
                "Notification is still ongoing",
                processingResult,
            )

            NotificationResultType.NOT_CONFIGURED -> WorkflowNotificationChannelNodeExecutorOutput.error(
                "Notification is not configured",
                processingResult
            )

            NotificationResultType.INVALID_CONFIGURATION -> WorkflowNotificationChannelNodeExecutorOutput.error(
                "Notification configuration is invalid",
                processingResult
            )

            NotificationResultType.DISABLED -> WorkflowNotificationChannelNodeExecutorOutput.error(
                "Notification is disabled",
                processingResult
            )

            NotificationResultType.ERROR -> WorkflowNotificationChannelNodeExecutorOutput.error(
                message ?: "Unknown error",
                processingResult
            )

            NotificationResultType.TIMEOUT -> WorkflowNotificationChannelNodeExecutorOutput.error(
                "Timeout when running a workflow",
                processingResult
            )

            else -> WorkflowNodeExecutorResult.error("Invalid notification result type: $type", null)
        }
}