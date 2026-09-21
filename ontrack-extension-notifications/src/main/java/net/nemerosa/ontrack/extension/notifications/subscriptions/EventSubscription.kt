package net.nemerosa.ontrack.extension.notifications.subscriptions

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.format
import net.nemerosa.ontrack.model.structure.ProjectEntity
import org.apache.commons.codec.digest.DigestUtils

/**
 * Subscription to an event.
 *
 * @property projectEntity Project entity to subscribe to (`null` for global events)
 * @property events List of events types to subscribe to
 * @property keywords Optional space-separated list of tokens to look for in the events
 * @property channel Type of channel to send the event to
 * @property channelConfig Specific configuration of the channel
 * @property disabled If the subscription is disabled
 * @property origin Origin of the subscription (used for filtering)
 * @property contentTemplate Optional template to use for the message
 */
data class EventSubscription(
    @APIDescription("Entity to subscribe to. Null for global subscriptions.")
    val projectEntity: ProjectEntity?,
    @APIDescription("Unique name of the subscription in its scope.")
    val name: String,
    @APIDescription("List of events types to subscribe to")
    val events: Set<String>,
    @APIDescription("Optional space-separated list of tokens to look for in the events")
    val keywords: String?,
    @APIDescription("Type of channel to send the event to")
    val channel: String,
    val channelConfig: JsonNode,
    @APIDescription("If the subscription is disabled")
    val disabled: Boolean,
    @APIDescription("Origin of the subscription (used for filtering)")
    val origin: String,
    @APIDescription("Optional template to use for the message")
    val contentTemplate: String?,
) {
    fun disabled(disabled: Boolean) = EventSubscription(
        projectEntity = projectEntity,
        name = name,
        events = events,
        keywords = keywords,
        channel = channel,
        channelConfig = channelConfig,
        disabled = disabled,
        origin = origin,
        contentTemplate = contentTemplate,
    )

    companion object {
        fun computeName(
            events: List<String>,
            keywords: String?,
            channel: String,
            channelConfig: JsonNode,
            contentTemplate: String?
        ): String =
            mapOf(
                "events" to events.sorted(),
                "keywords" to keywords,
                "channel" to channel,
                "channelConfig" to channelConfig,
                "contentTemplate" to contentTemplate,
            ).asJson().format().let {
                DigestUtils.md5Hex(it)
            }
    }
}