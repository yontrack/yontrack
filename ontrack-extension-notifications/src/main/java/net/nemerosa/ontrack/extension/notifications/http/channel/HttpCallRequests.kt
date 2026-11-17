package net.nemerosa.ontrack.extension.notifications.http.channel

import net.nemerosa.ontrack.extension.notifications.http.config.HttpConfiguration
import net.nemerosa.ontrack.json.transform
import org.springframework.http.HttpHeaders
import tools.jackson.databind.JsonNode

/**
 * Valid header name: an RFC 9110 token.
 */
private val headerName = Regex("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$")

/**
 * Checks the extra headers of a call against the HTTP configuration it goes through.
 *
 * `Authorization`, and any header set by the authentication of the configuration, are refused whatever
 * their case: the credentials come from the configuration only.
 *
 * @throws HttpCallConfigException If a header is refused
 */
fun checkHttpCallHeaders(
    headers: List<HttpNotificationChannelConfigHeader>,
    configuration: HttpConfiguration,
) {
    val reserved = configuration.authentication.headers().keys + HttpHeaders.AUTHORIZATION
    headers.forEach { header ->
        if (!headerName.matches(header.name)) {
            throw HttpCallConfigException("""Header name "${header.name}" is not valid.""")
        }
        if (reserved.any { it.equals(header.name, ignoreCase = true) }) {
            throw HttpCallConfigException(
                "Header ${header.name} cannot be set by the subscription: it is set by the HTTP configuration only."
            )
        }
    }
}

/**
 * Checks that a body is sent only by the verbs accepting one.
 *
 * @throws HttpCallConfigException If a body is given for a `GET`
 */
fun checkHttpCallBody(config: HttpNotificationChannelConfig) {
    if (config.actualBody != null && !config.method.acceptsBody) {
        throw HttpCallConfigException("A body cannot be sent with ${config.method}, only with POST or PUT.")
    }
}

/**
 * A JSON `null` body, as a form may send, is no body.
 */
private val HttpNotificationChannelConfig.actualBody: JsonNode?
    get() = body?.takeUnless { it.isNull || it.isMissingNode }

/**
 * Call once templated.
 *
 * @property method Verb of the call
 * @property path Templated path, not resolved yet under the base URL
 * @property headers Extra headers, with their templated values
 * @property body Templated body, if any
 */
data class HttpCallRequest(
    val method: HttpCallMethod,
    val path: String,
    val headers: List<Pair<String, String>>,
    val body: JsonNode?,
)

/**
 * Templates the path, the header values and the string leaves of the body of a call.
 *
 * @param render Rendering of a template
 */
fun HttpNotificationChannelConfig.renderHttpCallRequest(render: (String) -> String) = HttpCallRequest(
    method = method,
    path = render(path),
    headers = headers.map { it.name to render(it.value) },
    body = actualBody?.transform(render),
)
