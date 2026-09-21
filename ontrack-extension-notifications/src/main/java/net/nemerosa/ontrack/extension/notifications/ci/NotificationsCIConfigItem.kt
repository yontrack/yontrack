package net.nemerosa.ontrack.extension.notifications.ci

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.NullNode
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.extension.notifications.schema.NotificationDynamicJsonSchemaProvider
import net.nemerosa.ontrack.model.json.schema.DynamicJsonSchema
import net.nemerosa.ontrack.model.json.schema.JsonSchemaType

@DynamicJsonSchema(
    discriminatorProperty = "channel",
    configurationProperty = "channelConfig",
    provider = NotificationDynamicJsonSchemaProvider::class,
)
data class NotificationsCIConfigItem(
    @APIDescription("Unique name of the subscription in its scope.")
    val name: String,
    @APIDescription("Targeting a promotion instead of the branch. The promotion must have been configured first.")
    val promotion: String? = null,
    @APIDescription("List of events types to subscribe to")
    @JsonSchemaType(
        provider = EventTypeListJsonSchemaTypeProvider::class,
    )
    val events: List<String> = emptyList(),
    @APIDescription("Optional space-separated list of tokens to look for in the events")
    val keywords: String? = null,
    @APIDescription("Type of channel to send the event to")
    val channel: String? = null,
    val channelConfig: JsonNode = NullNode.instance,
    @APIDescription("Optional template to use for the message")
    val contentTemplate: String? = null,
)