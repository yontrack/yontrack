package net.nemerosa.ontrack.extension.notifications.http.channel

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import net.nemerosa.ontrack.extension.notifications.http.auth.HttpAuthentication
import net.nemerosa.ontrack.extension.notifications.http.config.HttpConfiguration
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parseAsJson
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.web.client.ResourceAccessException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.StringNode
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * HTTP calls over a real socket: `MockRestServiceServer` replaces the request factory, which is where
 * the redirects are turned off and the timeouts are set.
 */
class HttpCallClientTest {

    private val servers = mutableListOf<HttpServer>()

    @AfterEach
    fun after() {
        servers.forEach { it.stop(0) }
    }

    private class Received(
        val method: String,
        val path: String,
        val query: String?,
        val headers: Map<String, String?>,
        val body: String,
    )

    private val received = CopyOnWriteArrayList<Received>()

    private fun recordingServer(handler: (HttpExchange) -> Unit): HttpServer = server { exchange ->
        received += Received(
            method = exchange.requestMethod,
            path = exchange.requestURI.rawPath,
            query = exchange.requestURI.rawQuery,
            headers = exchange.requestHeaders.keys.associate { it.lowercase() to exchange.requestHeaders.getFirst(it) },
            body = exchange.requestBody.readAllBytes().toString(Charsets.UTF_8),
        )
        handler(exchange)
    }

    private fun call(
        server: HttpServer,
        method: HttpCallMethod = HttpCallMethod.GET,
        path: String = "/deployments",
        headers: List<Pair<String, String>> = emptyList(),
        body: JsonNode? = null,
        authentication: HttpAuthentication = HttpAuthentication.none(),
        requestTimeout: Duration = Duration.ofSeconds(5),
        maxResponseBytes: Int = 65536,
    ) = executeHttpCall(
        configuration = HttpConfiguration(name = "api", url = url(server), authentication = authentication),
        method = method,
        uri = URI(url(server) + path),
        headers = headers,
        body = body,
        requestTimeout = requestTimeout,
        maxResponseBytes = maxResponseBytes,
    )

    @Test
    fun `GET without a body`() {
        val api = recordingServer { it.respond(200, """{"status":"RUNNING"}""", "application/json") }
        val response = call(api, path = "/deployments/12?full=true")
        assertEquals(200, response.status)
        assertEquals("""{"status":"RUNNING"}""".parseAsJson(), response.bodyAsJson())
        assertFalse(response.truncated)
        val request = received.single()
        assertEquals("GET", request.method)
        assertEquals("/api/deployments/12", request.path)
        assertEquals("full=true", request.query)
        assertEquals("", request.body)
        assertNull(request.headers["content-type"])
    }

    @ParameterizedTest
    @ValueSource(strings = ["POST", "PUT"])
    fun `POST and PUT with a JSON body`(method: String) {
        val api = recordingServer { it.respond(201, """{"id":"d-1"}""", "application/json") }
        val response = call(
            api,
            method = HttpCallMethod.valueOf(method),
            body = mapOf("version" to "1.0.0", "replicas" to 3).asJson(),
        )
        assertEquals(201, response.status)
        val request = received.single()
        assertEquals(method, request.method)
        assertEquals("application/json", request.headers["content-type"])
        assertEquals(mapOf("version" to "1.0.0", "replicas" to 3).asJson(), request.body.parseAsJson())
    }

    @ParameterizedTest
    @ValueSource(strings = ["POST", "PUT"])
    fun `POST and PUT without a body`(method: String) {
        val api = recordingServer { it.respond(204) }
        val response = call(api, method = HttpCallMethod.valueOf(method))
        assertEquals(204, response.status)
        assertNull(response.bodyAsJson())
        assertEquals(method, received.single().method)
        assertEquals("", received.single().body)
    }

    @Test
    fun `Extra headers and the authentication of the configuration are sent`() {
        val api = recordingServer { it.respond(200) }
        call(
            api,
            headers = listOf("X-Request-Id" to "r-1", "Accept" to "application/json"),
            authentication = HttpAuthentication("bearer", mapOf("token" to "secret").asJson()),
        )
        val request = received.single()
        assertEquals("r-1", request.headers["x-request-id"])
        assertEquals("application/json", request.headers["accept"])
        assertEquals("Bearer secret", request.headers["authorization"])
    }

    @Test
    fun `An extra Content-Type replaces the JSON one`() {
        val api = recordingServer { it.respond(200) }
        call(
            api,
            method = HttpCallMethod.PUT,
            headers = listOf("Content-Type" to "application/merge-patch+json"),
            body = mapOf("a" to 1).asJson(),
        )
        assertEquals("application/merge-patch+json", received.single().headers["content-type"])
    }

    @ParameterizedTest
    @ValueSource(ints = [400, 404, 409, 500, 503])
    fun `Error statuses are returned with their body`(status: Int) {
        val api = server { it.respond(status, """{"error":"nope"}""", "application/json") }
        val response = call(api)
        assertEquals(status, response.status)
        assertEquals("""{"error":"nope"}""".parseAsJson(), response.bodyAsJson())
    }

    @ParameterizedTest
    @ValueSource(ints = [404, 500])
    fun `Error statuses without a body`(status: Int) {
        val api = server { it.respond(status) }
        val response = call(api)
        assertEquals(status, response.status)
        assertNull(response.bodyAsJson())
    }

    @Test
    fun `A redirect is returned as it is, and not followed`() {
        val reachedElsewhere = CopyOnWriteArrayList<String?>()
        val elsewhere = server { exchange ->
            reachedElsewhere += exchange.requestHeaders.getFirst("Authorization")
            exchange.respond(200)
        }
        val api = server { exchange ->
            exchange.responseHeaders.set("Location", url(elsewhere))
            exchange.respond(302)
        }
        val response = call(api, authentication = HttpAuthentication("bearer", mapOf("token" to "secret").asJson()))
        assertEquals(302, response.status)
        assertEquals(url(elsewhere), response.headers.getFirst("Location"))
        assertEquals(emptyList(), reachedElsewhere.toList(), "The redirect was not followed")
    }

    @Test
    fun `A body longer than the limit is truncated`() {
        val api = server { it.respond(200, "0123456789".repeat(10), "text/plain") }
        val response = call(api, maxResponseBytes = 25)
        assertTrue(response.truncated)
        assertEquals(StringNode("0123456789012345678901234"), response.bodyAsJson())
    }

    @Test
    fun `A body of exactly the limit is not truncated`() {
        val api = server { it.respond(200, "0123456789", "text/plain") }
        val response = call(api, maxResponseBytes = 10)
        assertFalse(response.truncated)
        assertEquals(StringNode("0123456789"), response.bodyAsJson())
    }

    @Test
    fun `A truncated JSON body is kept as text`() {
        val api = server { it.respond(200, """{"items":["a","b","c"]}""", "application/json") }
        val response = call(api, maxResponseBytes = 10)
        assertTrue(response.truncated)
        assertEquals(StringNode("""{"items":["""), response.bodyAsJson())
    }

    @ParameterizedTest
    @ValueSource(strings = ["application/json", "application/json; charset=utf-8", "application/vnd.api+json", "application/problem+json"])
    fun `JSON content types are parsed`(contentType: String) {
        val api = server { it.respond(200, """{"a":"é"}""", contentType) }
        assertEquals("""{"a":"é"}""".parseAsJson(), call(api).bodyAsJson())
    }

    @Test
    fun `Other content types are kept as text`() {
        val api = server { it.respond(200, """{"a":1}""", "text/plain") }
        assertEquals(StringNode("""{"a":1}"""), call(api).bodyAsJson())
    }

    @Test
    fun `An invalid JSON body is kept as text`() {
        val api = server { it.respond(200, "<html>oops</html>", "application/json") }
        assertEquals(StringNode("<html>oops</html>"), call(api).bodyAsJson())
    }

    @Test
    fun `A request longer than the timeout fails`() {
        val api = server { exchange ->
            Thread.sleep(3_000)
            exchange.respond(200)
        }
        assertFailsWith<ResourceAccessException> {
            call(api, requestTimeout = Duration.ofSeconds(1))
        }
    }

    @Test
    fun `A transport error is an exception`() {
        val api = server { it.respond(200) }
        val url = url(api)
        api.stop(0)
        assertFailsWith<ResourceAccessException> {
            executeHttpCall(
                configuration = HttpConfiguration(name = "api", url = url),
                method = HttpCallMethod.GET,
                uri = URI(url),
                headers = emptyList(),
                body = null,
                requestTimeout = Duration.ofSeconds(5),
                maxResponseBytes = 1024,
            )
        }
    }

    // Response headers kept in the output

    @Test
    fun `Response headers in the output, without secrets`() {
        val api = server { exchange ->
            exchange.responseHeaders.add("Location", "/api/deployments/12")
            exchange.responseHeaders.add("X-Trace", "a")
            exchange.responseHeaders.add("X-Trace", "b")
            exchange.responseHeaders.add("Set-Cookie", "session=s3cr3t")
            exchange.responseHeaders.add("Authorization", "Bearer echoed")
            exchange.responseHeaders.add("X-Api-Key", "echoed-key")
            exchange.respond(201)
        }
        val configuration = HttpConfiguration(
            name = "api",
            url = url(api),
            authentication = HttpAuthentication("header", mapOf("name" to "X-Api-Key", "value" to "k").asJson()),
        )
        val response = executeHttpCall(
            configuration = configuration,
            method = HttpCallMethod.POST,
            uri = URI(url(api)),
            headers = emptyList(),
            body = null,
            requestTimeout = Duration.ofSeconds(5),
            maxResponseBytes = 1024,
        )
        val headers = response.outputHeaders(configuration)
        assertEquals("/api/deployments/12", headers["location"])
        assertEquals("a, b", headers["x-trace"])
        assertFalse(headers.containsKey("set-cookie"))
        assertFalse(headers.containsKey("authorization"))
        assertFalse(headers.containsKey("x-api-key"))
        assertFalse(headers.values.any { it.contains("s3cr3t") || it.contains("echoed") })
    }

    private fun server(handler: (HttpExchange) -> Unit): HttpServer =
        HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
            createContext("/") { exchange ->
                try {
                    handler(exchange)
                } finally {
                    exchange.close()
                }
            }
            start()
            servers += this
        }

    private fun url(server: HttpServer): String =
        "http://${server.address.hostString}:${server.address.port}/api"

    private fun HttpExchange.respond(status: Int, body: String? = null, contentType: String? = null) {
        if (contentType != null) {
            responseHeaders.set("Content-Type", contentType)
        }
        if (body == null) {
            sendResponseHeaders(status, -1L)
        } else {
            val bytes = body.toByteArray(Charsets.UTF_8)
            sendResponseHeaders(status, bytes.size.toLong())
            responseBody.write(bytes)
        }
    }
}
