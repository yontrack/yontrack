package net.nemerosa.ontrack.extension.notifications.http.channel

import net.nemerosa.ontrack.extension.notifications.http.auth.HttpAuthentication
import net.nemerosa.ontrack.extension.notifications.http.config.HttpConfiguration
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parseAsJson
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class HttpCallRequestsTest {

    private val anonymous = HttpConfiguration(name = "api", url = "https://api.example.com")

    private val withApiKey = HttpConfiguration(
        name = "api",
        url = "https://api.example.com",
        authentication = HttpAuthentication(
            type = "header",
            config = mapOf("name" to "X-Api-Key", "value" to "secret").asJson(),
        ),
    )

    private fun config(
        method: HttpCallMethod = HttpCallMethod.POST,
        path: String = "/deployments",
        headers: List<HttpNotificationChannelConfigHeader> = emptyList(),
        body: String? = null,
    ) = HttpNotificationChannelConfig(
        config = "api",
        method = method,
        path = path,
        headers = headers,
        body = body?.parseAsJson(),
    )

    // Headers

    @Test
    fun `Extra headers are accepted`() {
        checkHttpCallHeaders(
            listOf(
                HttpNotificationChannelConfigHeader("Accept", "application/json"),
                HttpNotificationChannelConfigHeader("X-Request-Id", "\${build}"),
            ),
            withApiKey,
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["Authorization", "authorization", "AUTHORIZATION"])
    fun `The Authorization header is refused whatever its case`(name: String) {
        val ex = assertFailsWith<HttpCallConfigException> {
            checkHttpCallHeaders(listOf(HttpNotificationChannelConfigHeader(name, "Bearer x")), anonymous)
        }
        assertEquals(
            "Header $name cannot be set by the subscription: it is set by the HTTP configuration only.",
            ex.message
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["X-Api-Key", "x-api-key"])
    fun `A header set by the configuration is refused whatever its case`(name: String) {
        assertFailsWith<HttpCallConfigException> {
            checkHttpCallHeaders(listOf(HttpNotificationChannelConfigHeader(name, "other")), withApiKey)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " ", "X Header", "X-Header:", "X-Héader"])
    fun `Invalid header names are refused`(name: String) {
        assertFailsWith<HttpCallConfigException> {
            checkHttpCallHeaders(listOf(HttpNotificationChannelConfigHeader(name, "value")), anonymous)
        }
    }

    // Body

    @Test
    fun `A body is refused on GET`() {
        val ex = assertFailsWith<HttpCallConfigException> {
            checkHttpCallBody(config(method = HttpCallMethod.GET, body = """{"a":1}"""))
        }
        assertEquals("A body cannot be sent with GET, only with POST or PUT.", ex.message)
    }

    @Test
    fun `A JSON null body is no body, also on GET`() {
        checkHttpCallBody(config(method = HttpCallMethod.GET, body = "null"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["POST", "PUT"])
    fun `A body is accepted on POST and PUT`(method: String) {
        checkHttpCallBody(config(method = HttpCallMethod.valueOf(method), body = """{"a":1}"""))
    }

    // Rendering

    private val render: (String) -> String = { it.replace("\${build}", "1.0.0") }

    @Test
    fun `Path, header values and the string leaves of the body are templated`() {
        val request = config(
            path = "/deployments/\${build}?force=true",
            headers = listOf(HttpNotificationChannelConfigHeader("X-\${build}", "Build \${build}")),
            body = """
                {
                  "version": "${'$'}{build}",
                  "replicas": 3,
                  "dryRun": false,
                  "labels": ["${'$'}{build}", "fixed"],
                  "target": {"name": "v${'$'}{build}", "none": null}
                }
            """,
        ).renderHttpCallRequest(render)

        assertEquals(HttpCallMethod.POST, request.method)
        assertEquals("/deployments/1.0.0?force=true", request.path)
        assertEquals(listOf("X-\${build}" to "Build 1.0.0"), request.headers)
        assertEquals(
            """
                {
                  "version": "1.0.0",
                  "replicas": 3,
                  "dryRun": false,
                  "labels": ["1.0.0", "fixed"],
                  "target": {"name": "v1.0.0", "none": null}
                }
            """.parseAsJson(),
            request.body
        )
    }

    @Test
    fun `No body is rendered as no body`() {
        assertNull(config(body = null).renderHttpCallRequest(render).body)
        assertNull(config(body = "null").renderHttpCallRequest(render).body)
    }

    @Test
    fun `A body which is a single string is templated`() {
        assertEquals(
            "1.0.0".asJson(),
            config(body = "\"\${build}\"").renderHttpCallRequest(render).body
        )
    }
}
