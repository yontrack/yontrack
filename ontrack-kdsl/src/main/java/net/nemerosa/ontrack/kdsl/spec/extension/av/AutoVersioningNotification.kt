package net.nemerosa.ontrack.kdsl.spec.extension.av

import tools.jackson.databind.JsonNode

/**
 * Subscription to an auto versioning event.
 *
 * @property scope List of events to listen to
 * @property channel ID of the channel to listen to
 * @property config JSON configuration for the subscription
 * @property notificationTemplate Optional notification template used to override the default text
 */
data class AutoVersioningNotification(
    val channel: String,
    val config: JsonNode,
    val scope: List<AutoVersioningNotificationScope> = listOf(AutoVersioningNotificationScope.ALL),
    val notificationTemplate: String? = null,
)
