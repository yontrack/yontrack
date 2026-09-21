package net.nemerosa.ontrack.extension.notifications.model

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.model.events.Event

/**
 * Notification item, for one given channel and one event.
 *
 * @property source Source of the notification, like a global subscription, an entity subscription, a node in a workflow, etc.
 */
data class Notification(
    val source: NotificationSourceData?,
    val channel: String,
    val channelConfig: JsonNode,
    val event: Event,
    val template: String?,
)