package net.nemerosa.ontrack.extension.notifications.inmemory

import tools.jackson.databind.JsonNode
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import net.nemerosa.ontrack.common.RunProfile
import net.nemerosa.ontrack.extension.notifications.channels.AbstractNotificationChannel
import net.nemerosa.ontrack.extension.notifications.channels.NotificationResult
import net.nemerosa.ontrack.extension.notifications.subscriptions.EventSubscriptionConfigException
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.patchNullableString
import net.nemerosa.ontrack.json.patchString
import net.nemerosa.ontrack.model.Ack
import net.nemerosa.ontrack.model.docs.Documentation
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventTemplatingService
import net.nemerosa.ontrack.model.events.PlainEventRenderer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * Storing the messages in memory.
 *
 * For test only, should not be used in production.
 */
@Component
@Profile(RunProfile.DEV)
@Documentation(InMemoryNotificationChannelConfig::class)
class InMemoryNotificationChannel(
    private val eventTemplatingService: EventTemplatingService,
) :
    AbstractNotificationChannel<InMemoryNotificationChannelConfig, InMemoryNotificationChannelOutput>(
        InMemoryNotificationChannelConfig::class
    ) {

    private val logger: Logger = LoggerFactory.getLogger(InMemoryNotificationChannel::class.java)

    override fun validateParsedConfig(config: InMemoryNotificationChannelConfig) {
        if (config.group.isBlank()) {
            throw EventSubscriptionConfigException("Group cannot be blank")
        }
    }

    override fun mergeConfig(
        a: InMemoryNotificationChannelConfig,
        changes: JsonNode
    ) = InMemoryNotificationChannelConfig(
        group = patchString(changes, a::group),
        data = patchNullableString(changes, a::data),
    )

    private val messages = mutableMapOf<String, MutableList<String>>()

    internal fun getMessages(group: String) = messages[group] ?: emptyList()

    override fun publish(
        recordId: String,
        config: InMemoryNotificationChannelConfig,
        event: Event,
        context: Map<String, Any>,
        template: String?,
        outputProgressCallback: (current: InMemoryNotificationChannelOutput) -> InMemoryNotificationChannelOutput
    ): NotificationResult<InMemoryNotificationChannelOutput> {
        val text = eventTemplatingService.renderEvent(
            event,
            context,
            template,
            PlainEventRenderer()
        )

        if (config.waitMs != null) {
            logger.info("Waiting ${config.waitMs}ms before sending the message")
            runBlocking {
                delay(config.waitMs)
            }
        }

        logger.info("Sending message to group ${config.group}: $text")
        messages.getOrPut(config.group) { mutableListOf() }.add(text)
        return NotificationResult.ok(InMemoryNotificationChannelOutput(sent = true, data = config.data))
    }

    override fun toSearchCriteria(text: String): JsonNode = InMemoryNotificationChannelConfig(text).asJson()

    fun clear(): Ack {
        messages.clear()
        return Ack.OK
    }

    override val type: String = "in-memory"

    override val displayName: String = "In memory"

    override val enabled: Boolean = true
}