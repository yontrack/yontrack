package net.nemerosa.ontrack.extension.notifications.mock

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import net.nemerosa.ontrack.extension.notifications.channels.AbstractNotificationChannel
import net.nemerosa.ontrack.extension.notifications.channels.NotificationResult
import net.nemerosa.ontrack.extension.notifications.subscriptions.EventSubscriptionConfigException
import net.nemerosa.ontrack.it.waitUntil
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.patchNullableString
import net.nemerosa.ontrack.json.patchString
import net.nemerosa.ontrack.model.docs.Documentation
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventRendererRegistry
import net.nemerosa.ontrack.model.events.EventTemplatingService
import net.nemerosa.ontrack.model.events.PlainEventRenderer
import org.springframework.stereotype.Component
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeoutException
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

@Component
@Documentation(MockNotificationChannelConfig::class)
class MockNotificationChannel(
    private val eventTemplatingService: EventTemplatingService,
    private val eventRendererRegistry: EventRendererRegistry,
) :
    AbstractNotificationChannel<MockNotificationChannelConfig, MockNotificationChannelOutput>(
        MockNotificationChannelConfig::class
    ) {

    override fun validateParsedConfig(config: MockNotificationChannelConfig) {
        if (config.target.isBlank()) {
            throw EventSubscriptionConfigException("Target cannot be blank")
        }
    }

    override fun mergeConfig(
        a: MockNotificationChannelConfig,
        changes: JsonNode
    ) = MockNotificationChannelConfig(
        target = patchString(changes, a::target),
        data = patchNullableString(changes, a::data),
        rendererType = patchNullableString(changes, a::rendererType),
    )

    /**
     * List of messages received, indexed by target.
     *
     * Messages are published from the threads of the queue and of the workflow engine, and read back
     * from the test thread: a plain map and plain lists here would both lose updates and leave the
     * reader without any happens-before edge to the writer.
     *
     * Read this through [targetMessages], which takes the per-target list's own lock: reaching into
     * the map directly is safe for a lookup, but iterating the list it returns is not, since a
     * notification may be appended to it at any moment.
     */
    val messages = ConcurrentHashMap<String, MutableList<String>>()

    /**
     * Utility method to get the list of messages for a given target
     */
    fun targetMessages(target: String): List<String> =
        messages[target]?.let { list -> synchronized(list) { list.toList() } } ?: emptyList()

    /**
     * Utility method to wait until a given message has been received
     */
    @OptIn(ExperimentalTime::class)
    fun waitUntilReceivedMessage(
        what: String,
        target: String,
        expectedMessage: String,
        timeout: Duration = 10.seconds,
        interval: Duration = 250.milliseconds,
    ) {
        waitForTarget(
            what = what,
            target = target,
            expectation = "its first message to be:\n$expectedMessage",
            timeout = timeout,
            interval = interval,
        ) { targetMessages ->
            targetMessages.firstOrNull()?.trim() == expectedMessage
        }
    }

    /**
     * Utility method to wait until a given number of messages has been received
     */
    @OptIn(ExperimentalTime::class)
    fun waitUntilReceivedCountMessages(
        what: String,
        target: String,
        expectedCount: Int,
        timeout: Duration = 10.seconds,
        interval: Duration = 250.milliseconds,
    ) {
        waitForTarget(
            what = what,
            target = target,
            expectation = "$expectedCount message(s)",
            timeout = timeout,
            interval = interval,
        ) { targetMessages ->
            targetMessages.size == expectedCount
        }
    }

    /**
     * Waits for the messages of a [target] to satisfy [check].
     *
     * On timeout, fails with what the target actually held: a bare `TimeoutException` is the most
     * expensive kind of test failure to diagnose, since it does not say whether nothing was ever
     * published or whether something other than the expected message was.
     */
    @OptIn(ExperimentalTime::class)
    private fun waitForTarget(
        what: String,
        target: String,
        expectation: String,
        timeout: Duration,
        interval: Duration,
        check: (targetMessages: List<String>) -> Boolean,
    ) {
        try {
            waitUntil(
                message = what,
                timeout = timeout,
                interval = interval,
            ) {
                check(targetMessages(target))
            }
        } catch (ex: TimeoutException) {
            val actual = targetMessages(target)
            val rendering = if (actual.isEmpty()) {
                "nothing"
            } else {
                actual.joinToString("\n") { "- ${it.trim()}" }
            }
            fail(
                "$what: after $timeout, target '$target' was expected to hold $expectation - but held:\n$rendering",
                ex
            )
        }
    }

    override fun publish(
        recordId: String,
        config: MockNotificationChannelConfig,
        event: Event,
        context: Map<String, Any>,
        template: String?,
        outputProgressCallback: (current: MockNotificationChannelOutput) -> MockNotificationChannelOutput
    ): NotificationResult<MockNotificationChannelOutput> {
        val rendererType = config.rendererType ?: PlainEventRenderer.INSTANCE.id
        val renderer = eventRendererRegistry.findEventRendererById(rendererType)
            ?: PlainEventRenderer.INSTANCE
        val text = eventTemplatingService.renderEvent(
            event = event,
            context = context,
            template = template,
            renderer = renderer,
        )

        if (config.waitMs != null) {
            runBlocking {
                delay(config.waitMs)
            }
        }

        messages.computeIfAbsent(config.target) { Collections.synchronizedList(mutableListOf()) }.add(text)
        return NotificationResult.ok(
            output = MockNotificationChannelOutput(text = text, data = config.data)
        )
    }

    override fun toSearchCriteria(text: String): JsonNode =
        mapOf(MockNotificationChannelConfig::target.name to text).asJson()

    override val type: String = "mock"

    override val displayName: String = "Mock"

    override val enabled: Boolean = true
}