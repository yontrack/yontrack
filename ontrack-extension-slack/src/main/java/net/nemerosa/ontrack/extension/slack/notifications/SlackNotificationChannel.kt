package net.nemerosa.ontrack.extension.slack.notifications

import com.fasterxml.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.extension.notifications.channels.AbstractNotificationChannel
import net.nemerosa.ontrack.extension.notifications.channels.NotificationResult
import net.nemerosa.ontrack.extension.notifications.subscriptions.EventSubscriptionConfigException
import net.nemerosa.ontrack.extension.slack.SlackSettings
import net.nemerosa.ontrack.extension.slack.service.SlackService
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.patchEnum
import net.nemerosa.ontrack.json.patchString
import net.nemerosa.ontrack.model.docs.Documentation
import net.nemerosa.ontrack.model.docs.DocumentationLink
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventTemplatingService
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import org.springframework.stereotype.Component

@Component
@APIDescription("Sending messages to Slack. The notification template is used for the message.")
@Documentation(SlackNotificationChannelConfig::class)
@Documentation(SlackNotificationChannelOutput::class, section = "output")
@DocumentationLink("slack", name = "Slack documentation")
class SlackNotificationChannel(
    private val slackService: SlackService,
    private val cachedSettingsService: CachedSettingsService,
    private val slackNotificationEventRenderer: SlackNotificationEventRenderer,
    private val eventTemplatingService: EventTemplatingService,
) : AbstractNotificationChannel<SlackNotificationChannelConfig, SlackNotificationChannelOutput>(
    SlackNotificationChannelConfig::class
) {

    override fun validateParsedConfig(config: SlackNotificationChannelConfig) {
        if (config.channel.isBlank()) {
            throw EventSubscriptionConfigException("Slack channel cannot be blank")
        }
    }

    override fun mergeConfig(
        a: SlackNotificationChannelConfig,
        changes: JsonNode
    ) = SlackNotificationChannelConfig(
        channel = patchString(changes, a::channel),
        type = patchEnum(changes, a::type),
    )

    override fun publish(
        recordId: String,
        config: SlackNotificationChannelConfig,
        event: Event,
        context: Map<String, Any>,
        template: String?,
        outputProgressCallback: (current: SlackNotificationChannelOutput) -> SlackNotificationChannelOutput
    ): NotificationResult<SlackNotificationChannelOutput> {
        // Formatting the message
        val message = format(event, context, template)
        // Sending the message
        val sent = slackService.sendNotification(config.channel, message, config.type)
        // Result
        return if (sent) {
            NotificationResult.ok(
                output = SlackNotificationChannelOutput(message = message)
            )
        } else {
            NotificationResult.error(
                message = "Slack message could not be sent. Check the operational logs.",
                output = SlackNotificationChannelOutput(message = message),
            )
        }
    }

    private fun format(event: Event, context: Map<String, Any>, template: String?): String =
        eventTemplatingService.renderEvent(
            event = event,
            context = context,
            template = template,
            renderer = slackNotificationEventRenderer,
        )

    override fun toSearchCriteria(text: String): JsonNode =
        mapOf(SlackNotificationChannelConfig::channel.name to text).asJson()

    override val type: String = "slack"

    override val displayName: String = "Slack"

    override val enabled: Boolean
        get() = try {
            cachedSettingsService.getCachedSettings(SlackSettings::class.java).enabled
        } catch (_: Exception) {
            false
        }
}