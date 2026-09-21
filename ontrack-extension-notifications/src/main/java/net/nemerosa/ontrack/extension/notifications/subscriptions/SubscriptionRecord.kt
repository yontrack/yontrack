package net.nemerosa.ontrack.extension.notifications.subscriptions

import tools.jackson.databind.JsonNode

/**
 * Subscription record, can be used for storage
 */
data class SubscriptionRecord(
    val name: String,
    val channel: String,
    val channelConfig: JsonNode,
    val events: Set<String>,
    val keywords: String?,
    val disabled: Boolean,
    val origin: String,
    val contentTemplate: String?,
) {
    fun withName(name: String) = SubscriptionRecord(
        name = name,
        channel = channel,
        channelConfig = channelConfig,
        events = events,
        keywords = keywords,
        disabled = disabled,
        origin = origin,
        contentTemplate = contentTemplate
    )
}
