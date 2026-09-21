package net.nemerosa.ontrack.extension.av.config

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.graphql.support.ListRef

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
    @ListRef
    val scope: List<AutoVersioningNotificationScope> = listOf(AutoVersioningNotificationScope.ALL),
    @APIDescription("Optional notification template used to override the default text")
    val notificationTemplate: String?,
) {
    companion object {
        /**
         * Origin of the notifications which are set by the auto versioning
         */
        const val ORIGIN = "auto-versioning"
    }
}