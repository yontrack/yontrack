package net.nemerosa.ontrack.extension.notifications.channels

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.notifications.recording.NotificationRecord
import net.nemerosa.ontrack.model.events.Event

/**
 * Makes the link between an event to be sent and an actual notification backend.
 *
 * @param C Type of the configuration for this channel.
 * @param R Type of output returned by the channel
 */
interface NotificationChannel<C, R> {

    fun validate(channelConfig: JsonNode): ValidatedNotificationChannelConfig<C>

    /**
     * Sends an event onto this channel.
     *
     * If the notification launches asynchronous processes that will participate
     * into its outcome and result, it should return an [asynchronous][NotificationResult.async]
     * result and computes its current state using the [getNotificationResult] function.
     *
     * @param recordId Unique ID for the recording of this notification
     * @param config Configuration for the channel
     * @param event Event to send notification about
     * @param context Notification template context
     * @param template Alternative template to the default event message
     * @param outputProgressCallback Gives the opportunity to the channel to register some progress on its output
     * @return Response by the channel
     */
    fun publish(
        recordId: String,
        config: C,
        event: Event,
        context: Map<String, Any>,
        template: String?,
        outputProgressCallback: (current: R) -> R,
    ): NotificationResult<R>

    /**
     * Getting the result of a notification dynamically.
     *
     * By default, returns null, meaning that the channel
     * immediately returns the notification result.
     *
     * If the notification's result is computed asynchronously, this function
     * is called to retrieve the current state of the notification.
     *
     * @param notificationRecord Notification to get the result for
     * @return Actual notification result or null if must be ignored
     */
    fun getNotificationResult(
        notificationRecord: NotificationRecord,
    ): NotificationResult<R>? = null

    /**
     * Given a search token, returns a configuration which would match a JSON.
     */
    fun toSearchCriteria(text: String): JsonNode

    /**
     * Merges two configurations.
     *
     * @param a First configuration, where to merge the [changes]
     * @param changes JSON containing the changes to apply to the [a] configuration
     * @return Merged configuration
     */
    fun mergeConfig(a: C, changes: JsonNode): C

    /**
     * Type of the channel, used as an identifier for the serialization of the subscriptions.
     */
    val type: String

    /**
     * Display name for the channel
     */
    val displayName: String

    /**
     * Is this channel enabled?
     */
    val enabled: Boolean

}