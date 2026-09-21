package net.nemerosa.ontrack.extension.notifications.subscriptions

import com.fasterxml.jackson.annotation.JsonAlias
import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.api.APIDescription

data class SubscriptionsCascContextData(
    @APIDescription("Name of the subscription. Will be required in V5.")
    val name: String?,
    @APIDescription("List of events to listen to")
    val events: List<String>,
    @APIDescription("Keywords to filter the events")
    val keywords: String?,
    @APIDescription("Channel to send notifications to")
    val channel: String,
    @APIDescription("Configuration of the channel")
    @JsonAlias("channel-config")
    val channelConfig: JsonNode,
    @APIDescription("Is this channel disabled?")
    val disabled: Boolean? = null,
    @APIDescription("Optional template to use for the message")
    val contentTemplate: String?,
) {
    fun normalized() = SubscriptionsCascContextData(
        name = actualName(),
        events = events.sorted(),
        keywords = keywords,
        channel = channel,
        channelConfig = channelConfig,
        disabled = disabled ?: false,
        contentTemplate = contentTemplate,
    )

    fun actualName(): String = name ?: computeName()

    fun computeName(): String =
        EventSubscription.computeName(
            events = events,
            keywords = keywords,
            channel = channel,
            channelConfig = channelConfig,
            contentTemplate = contentTemplate,
        )
}