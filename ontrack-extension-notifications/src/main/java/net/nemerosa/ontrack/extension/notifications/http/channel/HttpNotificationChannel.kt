package net.nemerosa.ontrack.extension.notifications.http.channel

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.extension.notifications.channels.AbstractNotificationChannel
import net.nemerosa.ontrack.extension.notifications.channels.NoTemplate
import net.nemerosa.ontrack.extension.notifications.channels.NotificationResult
import net.nemerosa.ontrack.extension.notifications.http.config.HttpConfiguration
import net.nemerosa.ontrack.extension.notifications.http.config.HttpConfigurationService
import net.nemerosa.ontrack.extension.notifications.http.settings.HttpNotificationSettings
import net.nemerosa.ontrack.extension.notifications.subscriptions.EventSubscriptionConfigException
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.patchEnum
import net.nemerosa.ontrack.json.patchInt
import net.nemerosa.ontrack.json.patchNullableString
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
import java.net.URI
import java.time.Duration

/**
 * The `http` channel: one `GET`, `POST` or `PUT` under an [HTTP configuration][net.nemerosa.ontrack.extension.notifications.http.config.HttpConfiguration].
 *
 * The call is always awaited: a 2xx is a success, anything else (a redirect included, since redirects
 * are not followed) or a transport error is an error.
 *
 * With a success condition, the call goes on waiting (decisions 13 to 20): the conditions are evaluated
 * on every response, the call is polled while neither holds, and the wait blocks inside [publish], as the
 * `SYNC` modes of the Jenkins, GitHub, GitLab and Bitbucket channels do.
 */
@Component
@APIDescription(
    """Calls an HTTP API under an HTTP configuration: one GET, POST or PUT, with a templated path, templated headers and a templated JSON body.
The call is always awaited: a 2xx response is a success, any other status (a redirect included, since redirects are never followed) or a transport error is an error.
With a success condition (CEL), the call goes on waiting: a GET is polled on its own URL, a POST or PUT on its status URL, until the success or the failure condition holds, or the timeout expires."""
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
          successCondition: "response.body.status == 'SUCCEEDED'"
          failureCondition: "response.body.status == 'FAILED'"
          statusUrl: "response.headers['location']"
          intervalSeconds: 10
          timeoutSeconds: 600
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
            HttpCallConditions.compile(config)
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
        successCondition = patchNullableString(changes, a::successCondition),
        failureCondition = patchNullableString(changes, a::failureCondition),
        statusUrl = patchNullableString(changes, a::statusUrl),
        intervalSeconds = patchInt(changes, a::intervalSeconds),
        timeoutSeconds = patchInt(changes, a::timeoutSeconds),
    )

    override fun toSearchCriteria(text: String): JsonNode =
        mapOf(HttpNotificationChannelConfig::config.name to text).asJson()

    /**
     * Time of the polling. Only the tests replace it, to poll without waiting.
     */
    internal var pollingClock: HttpCallPollingClock = HttpCallPollingClock.SYSTEM

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
        val (call, uri, conditions) = try {
            checkHttpCallBody(config)
            checkHttpCallHeaders(config.headers, configuration)
            val conditions = HttpCallConditions.compile(config)
            val rendered = config.renderHttpCallRequest { text ->
                eventTemplatingService.render(
                    template = text,
                    event = event,
                    context = context,
                    renderer = PlainEventRenderer.INSTANCE,
                )
            }
            // Checked after templating
            Triple(rendered, resolveHttpCallUrl(configuration.url, rendered.path), conditions)
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
        val send = { method: HttpCallMethod, target: URI, body: JsonNode? ->
            executeHttpCall(
                configuration = configuration,
                method = method,
                uri = target,
                headers = call.headers,
                body = body,
                requestTimeout = Duration.ofSeconds(settings.requestTimeoutSeconds.toLong()),
                maxResponseBytes = settings.maxResponseBytes,
            )
        }

        // The initial request, sent once
        val response = try {
            send(call.method, uri, call.body)
        } catch (any: Exception) {
            return NotificationResult.error(
                message = "${call.method} $uri failed: ${any.message}",
                output = outputProgressCallback(output.copy(attempts = 1)),
            )
        }

        output = output.withResponse(response, configuration, attempts = 1)

        val status = response.status
        if (status !in 200..299) {
            output = outputProgressCallback(output)
            return NotificationResult.error(
                message = unexpectedStatusMessage(call.method, uri, response),
                output = output,
            )
        }

        // No condition: any 2xx is a success
        if (conditions == null) {
            return NotificationResult.ok(outputProgressCallback(output))
        }

        // Conditions on the initial response
        val initialOutcome = try {
            conditions.evaluate(HttpCallCelContext.of(response, configuration, attempt = 1))
        } catch (ex: HttpCallCelEvaluationException) {
            return NotificationResult.error(
                message = "${call.method} $uri: ${ex.message}",
                output = outputProgressCallback(output),
            )
        }
        output = output.copy(condition = initialOutcome)
        when (initialOutcome) {
            HttpCallConditionOutcome.SUCCESS -> return NotificationResult.ok(outputProgressCallback(output))
            HttpCallConditionOutcome.FAILURE -> return NotificationResult.error(
                message = failureMessage(call.method, uri, conditions),
                output = outputProgressCallback(output),
            )

            HttpCallConditionOutcome.PENDING -> {}
        }

        // What to poll
        val pollUri = if (call.method == HttpCallMethod.GET) {
            uri
        } else if (conditions.statusUrl != null) {
            try {
                val path = conditions.statusUrl.evaluate(HttpCallCelContext.of(response, configuration, attempt = 1))
                resolveHttpCallStatusUrl(configuration.url, path)
            } catch (ex: HttpCallCelEvaluationException) {
                return NotificationResult.error(
                    message = "${call.method} $uri: ${ex.message}",
                    output = outputProgressCallback(output),
                )
            } catch (ex: HttpCallUrlException) {
                return NotificationResult.error(
                    message = "${call.method} $uri: the status URL cannot be polled. ${ex.message}",
                    output = outputProgressCallback(output),
                )
            }
        } else {
            return NotificationResult.error(
                message = "${call.method} $uri: the success condition `${conditions.success.expression}` does not hold on the response, and there is no status URL to poll.",
                output = outputProgressCallback(output),
            )
        }
        output = outputProgressCallback(output.copy(statusUrl = pollUri.toString()))

        // Polling
        val interval = Duration.ofSeconds(config.intervalSeconds.toLong())
        val timeoutSeconds = config.effectiveTimeoutSeconds(settings)
        val deadline = pollingClock.now().plusSeconds(timeoutSeconds.toLong())
        var attempt = 1
        var consecutiveErrors = 0
        while (true) {
            if (pollingClock.now().plus(interval).isAfter(deadline)) {
                return NotificationResult.error(
                    message = "${call.method} $uri: still pending, timed out after $timeoutSeconds s (polling GET $pollUri).",
                    output = output,
                )
            }
            try {
                pollingClock.sleep(interval)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return NotificationResult.error(
                    message = "${call.method} $uri: interrupted while polling GET $pollUri.",
                    output = output,
                )
            }
            attempt++

            val polled = try {
                send(HttpCallMethod.GET, pollUri, null)
            } catch (any: Exception) {
                consecutiveErrors++
                output = outputProgressCallback(
                    output.copy(
                        status = null,
                        headers = emptyMap(),
                        body = null,
                        truncated = false,
                        attempts = attempt,
                        condition = null,
                    )
                )
                if (consecutiveErrors > MAX_CONSECUTIVE_POLL_ERRORS) {
                    return NotificationResult.error(
                        message = "GET $pollUri failed $consecutiveErrors times in a row: ${any.message}",
                        output = output,
                    )
                }
                continue
            }

            output = output.withResponse(polled, configuration, attempts = attempt).copy(condition = null)
            val polledStatus = polled.status
            when {
                polledStatus in 200..299 -> {
                    consecutiveErrors = 0
                    val outcome = try {
                        conditions.evaluate(HttpCallCelContext.of(polled, configuration, attempt))
                    } catch (ex: HttpCallCelEvaluationException) {
                        return NotificationResult.error(
                            message = "GET $pollUri: ${ex.message}",
                            output = outputProgressCallback(output),
                        )
                    }
                    output = outputProgressCallback(output.copy(condition = outcome))
                    when (outcome) {
                        HttpCallConditionOutcome.SUCCESS -> return NotificationResult.ok(output)
                        HttpCallConditionOutcome.FAILURE -> return NotificationResult.error(
                            message = failureMessage(HttpCallMethod.GET, pollUri, conditions),
                            output = output,
                        )

                        HttpCallConditionOutcome.PENDING -> {}
                    }
                }

                polledStatus >= 500 || polledStatus == HttpStatus.TOO_MANY_REQUESTS.value() -> {
                    consecutiveErrors++
                    output = outputProgressCallback(output)
                    if (consecutiveErrors > MAX_CONSECUTIVE_POLL_ERRORS) {
                        return NotificationResult.error(
                            message = "GET $pollUri failed with ${statusText(polledStatus)} $consecutiveErrors times in a row.",
                            output = output,
                        )
                    }
                }

                else -> return NotificationResult.error(
                    message = unexpectedStatusMessage(HttpCallMethod.GET, pollUri, polled),
                    output = outputProgressCallback(output),
                )
            }
        }
    }

    private fun HttpNotificationChannelOutput.withResponse(
        response: HttpCallResponse,
        configuration: HttpConfiguration,
        attempts: Int,
    ) = copy(
        status = response.status,
        headers = response.outputHeaders(configuration),
        body = response.bodyAsJson(),
        truncated = response.truncated,
        attempts = attempts,
    )

    private fun statusText(status: Int) =
        listOfNotNull(status, HttpStatus.resolve(status)?.reasonPhrase).joinToString(" ")

    private fun unexpectedStatusMessage(method: HttpCallMethod, uri: URI, response: HttpCallResponse): String {
        val statusText = statusText(response.status)
        return if (response.status in 300..399) {
            "$method $uri was redirected ($statusText) to ${response.headers.getFirst(HttpHeaders.LOCATION)}, which is not followed."
        } else {
            "$method $uri failed with $statusText."
        }
    }

    private fun failureMessage(method: HttpCallMethod, uri: URI, conditions: HttpCallConditions) =
        "$method $uri: the failure condition `${conditions.failure?.expression}` holds."

    companion object {
        /**
         * Number of consecutive transport errors, 5xx or 429 tolerated while polling: one more is an error.
         */
        const val MAX_CONSECUTIVE_POLL_ERRORS = 3
    }
}
