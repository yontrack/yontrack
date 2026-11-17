package net.nemerosa.ontrack.extension.notifications.http.channel

import net.nemerosa.ontrack.extension.notifications.http.config.HttpConfiguration
import net.nemerosa.ontrack.extension.notifications.http.config.createHttpConfigurationRestTemplate
import net.nemerosa.ontrack.json.parseAsJson
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.StringNode
import java.io.IOException
import java.net.URI
import java.time.Duration

/**
 * Response to an HTTP call.
 *
 * @property status Status code
 * @property headers Response headers, as received
 * @property body Response body, at most the maximum response size
 * @property truncated True when the body was longer than the maximum response size
 */
class HttpCallResponse(
    val status: Int,
    val headers: HttpHeaders,
    val body: ByteArray,
    val truncated: Boolean,
) {

    /**
     * Body for the output: parsed when its content type is JSON and it was read entirely, as text
     * otherwise, absent when empty.
     */
    fun bodyAsJson(): JsonNode? {
        if (body.isEmpty()) return null
        val contentType = try {
            headers.contentType
        } catch (_: Exception) {
            null
        }
        val text = String(body, contentType?.charset ?: Charsets.UTF_8)
        return if (!truncated && contentType != null && contentType.isJson()) {
            try {
                text.parseAsJson()
            } catch (_: Exception) {
                StringNode(text)
            }
        } else {
            StringNode(text)
        }
    }

    /**
     * Headers for the output, by lowercase name, repeated values joined by a comma, without the
     * headers which could carry a secret: `Authorization`, `Set-Cookie` and the headers set by the
     * authentication of the configuration.
     */
    fun outputHeaders(configuration: HttpConfiguration): Map<String, String> {
        val dropped = (configuration.authentication.headers().keys + droppedHeaders)
            .map { it.lowercase() }
            .toSet()
        return headers.headerNames()
            .map { it.lowercase() }
            .distinct()
            .filter { it !in dropped }
            .associateWith { name -> headers.getOrEmpty(name).joinToString(", ") }
            .toSortedMap()
    }

    private fun MediaType.isJson() =
        isCompatibleWith(MediaType.APPLICATION_JSON) || subtype.endsWith("+json")

    companion object {
        private val droppedHeaders = listOf(
            HttpHeaders.AUTHORIZATION,
            HttpHeaders.PROXY_AUTHORIZATION,
            HttpHeaders.SET_COOKIE,
            "Set-Cookie2",
        )
    }
}

/**
 * Sends one request under an HTTP configuration.
 *
 * - The authentication of the configuration is added to the request.
 * - Redirects are not followed: a 3xx is returned as it is.
 * - The body, when any, is sent as `application/json`, unless the extra headers set another content type.
 * - The response body is read up to [maxResponseBytes], then cut.
 * - No status raises an error: the caller decides what the response means.
 *
 * @param configuration HTTP configuration of the call
 * @param method Verb
 * @param uri URL to call, already resolved under the base URL of the configuration
 * @param headers Extra headers
 * @param body JSON body, if any
 * @param requestTimeout Timeout of the connection, and of the reading of the response
 * @param maxResponseBytes Maximum number of bytes of the response body to keep
 * @throws org.springframework.web.client.RestClientException On a transport error (connection refused,
 * timeout, TLS...)
 */
fun executeHttpCall(
    configuration: HttpConfiguration,
    method: HttpCallMethod,
    uri: URI,
    headers: List<Pair<String, String>>,
    body: JsonNode?,
    requestTimeout: Duration,
    maxResponseBytes: Int,
): HttpCallResponse {
    val restTemplate = createHttpConfigurationRestTemplate(
        configuration = configuration,
        connectTimeout = requestTimeout,
        readTimeout = requestTimeout,
    )
    return restTemplate.execute(
        uri,
        HttpMethod.valueOf(method.name),
        { request ->
            if (body != null) {
                request.headers.contentType = MediaType.APPLICATION_JSON
            }
            headers.forEach { (name, value) ->
                request.headers.set(name, value)
            }
            if (body != null) {
                request.body.write(body.toString().toByteArray(Charsets.UTF_8))
            }
        },
        { response ->
            val status = response.statusCode.value()
            val responseHeaders = HttpHeaders.copyOf(response.headers)
            // An error status without any content (a bare 404 for example) has no stream to read
            val stream = try {
                response.body
            } catch (_: IOException) {
                null
            }
            val (bytes, truncated) = stream?.use { input ->
                val bytes = input.readNBytes(maxResponseBytes)
                bytes to (input.read() != -1)
            } ?: (ByteArray(0) to false)
            HttpCallResponse(
                status = status,
                headers = responseHeaders,
                body = bytes,
                truncated = truncated,
            )
        }
    ) ?: error("No response from $uri")
}
