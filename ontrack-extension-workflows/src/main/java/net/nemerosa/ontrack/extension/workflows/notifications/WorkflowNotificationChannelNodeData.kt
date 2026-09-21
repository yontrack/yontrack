package net.nemerosa.ontrack.extension.workflows.notifications

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.extension.notifications.schema.NotificationDynamicJsonSchemaProvider
import net.nemerosa.ontrack.model.json.schema.DynamicJsonSchema

@DynamicJsonSchema(
    discriminatorProperty = "channel",
    configurationProperty = "channelConfig",
    provider = NotificationDynamicJsonSchemaProvider::class,
)
data class WorkflowNotificationChannelNodeData(
    @APIDescription("Notification channel ID")
    val channel: String,
    @APIDescription("Notification channel configuration")
    val channelConfig: JsonNode,
    @APIDescription("Optional template for the notification")
    val template: String?,
)
