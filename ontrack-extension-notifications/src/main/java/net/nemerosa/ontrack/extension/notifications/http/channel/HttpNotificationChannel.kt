package net.nemerosa.ontrack.extension.notifications.http.channel

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.extension.notifications.channels.AbstractNotificationChannel
import net.nemerosa.ontrack.extension.notifications.channels.NoTemplate
import net.nemerosa.ontrack.extension.notifications.channels.NotificationResult
import net.nemerosa.ontrack.extension.notifications.http.config.HttpConfigurationService
import net.nemerosa.ontrack.extension.notifications.http.settings.HttpNotificationSettings
import net.nemerosa.ontrack.extension.notifications.subscriptions.EventSubscriptionConfigException
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.patchEnum
import net.nemerosa.ontrack.json.patchString
import net.nemerosa.ontrack.model.docs.Documentation
import net.nemerosa.ontrack.model.docs.DocumentationExampleCode
import net.nemerosa.ontrack.model.docs.DocumentationLink
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventTemplatingService
import net.nemerosa.ontrack.model.events.PlainEventRenderer
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import net.nemerosa.ontrack.model.utils.patchList
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import java.time.Duration

/**
 * The `http` channel: one `GET`, `POST` or `PUT` under an [HTTP configuration][net.nemerosa.ontrack.extension.notifications.http.config.HttpConfiguration].
 *
 * The call is always awaited: a 2xx is a success, anything else (a redirect included, since redirects
 * are not followed) or a transport error is an error.
 */
@Component
@APIDescription(
    """Calls an HTTP API under an HTTP configuration: one GET, POST or PUT, with a templated path, templated headers and a templated JSON body.
The call is always awaited: a 2xx response is a success, any other status (a redirect included, since redirects are never followed) or a transport error is an error."""
)
@Documentation(HttpNotificationChannelConfig::class)
@Documentation(HttpNotificationChannelOutput::class, section = "output")
@DocumentationLink(value = "integrations/notifications/http.md", name = "HTTP call")
@DocumentationExampleCode(
    $$"""
        channel: http
        channelConfig:
          config: deployments
          method: POST
          path: /deployments?project=${project}
          headers:
            - name: X-Request-Id
              value: "${build}"
          body:
            version: "${build}"
            environment: production
            replicas: 3
    """
)
@NoTemplate
class HttpNotificationChannel(
    private val httpConfigurationService: HttpConfigurationService,
    private val eventTemplatingService: EventTemplatingService,
    private val cachedSettingsService: CachedSettingsService,
) : AbstractNotificationChannel<HttpNotificationChannelConfig, HttpNotificationChannelOutput>(
    HttpNotificationChannelConfig::class
) {

    override val type: String = "http"

    override val displayName: String = "HTTP call"

    override val enabled: Boolean = true

    override fun validateParsedConfig(config: HttpNotificationChannelConfig) {
        if (config.config.isBlank()) {
            throw EventSubscriptionConfigException("HTTP configuration name is required")
        }
        val configuration = httpConfigurationService.findConfiguration(config.config)
            ?: throw EventSubscriptionConfigException("HTTP configuration ${config.config} could not be found")
        try {
            checkHttpCallBody(config)
            checkHttpCallHeaders(config.headers, configuration)
        } catch (ex: HttpCallConfigException) {
            throw EventSubscriptionConfigException(ex.message ?: "Invalid HTTP call")
        }
    }

    override fun mergeConfig(
        a: HttpNotificationChannelConfig,
        changes: JsonNode
    ) = HttpNotificationChannelConfig(
        config = patchString(changes, a::config),
        method = patchEnum(changes, a::method),
        path = patchString(changes, a::path),
        headers = patchList(changes, a::headers) { it.name },
        body = if (changes.has(HttpNotificationChannelConfig::body.name)) {
            changes.get(HttpNotificationChannelConfig::body.name)
        } else {
            a.body
        },
    )

    override fun toSearchCriteria(text: String): JsonNode =
        mapOf(HttpNotificationChannelConfig::config.name to text).asJson()

    override fun publish(
        recordId: String,
        config: HttpNotificationChannelConfig,
        event: Event,
        context: Map<String, Any>,
        template: String?,
        outputProgressCallback: (current: HttpNotificationChannelOutput) -> HttpNotificationChannelOutput
    ): NotificationResult<HttpNotificationChannelOutput> {
        val configuration = httpConfigurationService.findConfiguration(config.config)
            ?: return NotificationResult.invalidConfiguration("HTTP configuration cannot be found: ${config.config}")

        // The configuration may have changed since the subscription was saved
        val (call, uri) = try {
            checkHttpCallBody(config)
            checkHttpCallHeaders(config.headers, configuration)
            val rendered = config.renderHttpCallRequest { text ->
                eventTemplatingService.render(
                    template = text,
                    event = event,
                    context = context,
                    renderer = PlainEventRenderer.INSTANCE,
                )
            }
            // Checked after templating
            rendered to resolveHttpCallUrl(configuration.url, rendered.path)
        } catch (ex: HttpCallConfigException) {
            return NotificationResult.invalidConfiguration(ex.message)
        }

        var output = outputProgressCallback(
            HttpNotificationChannelOutput(
                method = call.method,
                url = uri.toString(),
            )
        )

        val settings = cachedSettingsService.getCachedSettings(HttpNotificationSettings::class.java)
        val response = try {
            executeHttpCall(
                configuration = configuration,
                method = call.method,
                uri = uri,
                headers = call.headers,
                body = call.body,
                requestTimeout = Duration.ofSeconds(settings.requestTimeoutSeconds.toLong()),
                maxResponseBytes = settings.maxResponseBytes,
            )
        } catch (any: Exception) {
            return NotificationResult.error(
                message = "${call.method} $uri failed: ${any.message}",
                output = outputProgressCallback(output.copy(attempts = 1)),
            )
        }

        output = outputProgressCallback(
            output.copy(
                status = response.status,
                headers = response.outputHeaders(configuration),
                body = response.bodyAsJson(),
                truncated = response.truncated,
                attempts = 1,
            )
        )

        val status = response.status
        val statusText = listOfNotNull(status, HttpStatus.resolve(status)?.reasonPhrase).joinToString(" ")
        return when (status) {
            in 200..299 -> NotificationResult.ok(output)
            in 300..399 -> NotificationResult.error(
                message = "${call.method} $uri was redirected ($statusText) to ${response.headers.getFirst(HttpHeaders.LOCATION)}, which is not followed.",
                output = output,
            )

            else -> NotificationResult.error(
                message = "${call.method} $uri failed with $statusText.",
                output = output,
            )
        }
    }
}
