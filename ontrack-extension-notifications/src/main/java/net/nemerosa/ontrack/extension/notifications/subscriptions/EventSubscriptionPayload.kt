package net.nemerosa.ontrack.extension.notifications.subscriptions

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.api.APIDescription

@APIDescription("Event subscription record")
data class EventSubscriptionPayload(
    @APIDescription("Unique name for this subscription")
    val name: String,
    @APIDescription("Channel to send this event to")
    val channel: String,
    @APIDescription("Channel configuration")
    val channelConfig: JsonNode,
    @APIDescription("List of events types to subscribe to")
    val events: List<String>,
    @APIDescription("Optional space-separated list of tokens to look for in the events")
    val keywords: String?,
    @APIDescription("If this subscription is disabled")
    val disabled: Boolean,
    @APIDescription("Origin of the subscription (used for filtering)")
    val origin: String,
    @APIDescription("Optional template to use for the message")
    val contentTemplate: String?,
)