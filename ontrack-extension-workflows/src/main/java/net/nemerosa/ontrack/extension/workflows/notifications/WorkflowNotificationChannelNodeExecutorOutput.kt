package net.nemerosa.ontrack.extension.workflows.notifications

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.notifications.processing.NotificationProcessingResult
import net.nemerosa.ontrack.extension.workflows.execution.WorkflowNodeExecutorResult
import net.nemerosa.ontrack.json.asJson

data class WorkflowNotificationChannelNodeExecutorOutput(
    val recordId: String?,
    val result: JsonNode?,
) {
    companion object {

        fun success(processingResult: NotificationProcessingResult<*>?) =
            WorkflowNodeExecutorResult.success(
                output = WorkflowNotificationChannelNodeExecutorOutput(
                    recordId = processingResult?.recordId,
                    result = processingResult?.result?.output?.asJson(),
                ).asJson()
            )

        fun error(message: String, processingResult: NotificationProcessingResult<*>?) =
            WorkflowNodeExecutorResult.error(
                message = message,
                output = WorkflowNotificationChannelNodeExecutorOutput(
                    recordId = processingResult?.recordId,
                    result = processingResult?.result?.output?.asJson(),
                ).asJson()
            )

    }
}
