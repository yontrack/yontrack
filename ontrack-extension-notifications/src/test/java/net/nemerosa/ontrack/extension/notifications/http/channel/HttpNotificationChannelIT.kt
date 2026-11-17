package net.nemerosa.ontrack.extension.notifications.http.channel

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import net.nemerosa.ontrack.extension.notifications.AbstractNotificationTestSupport
import net.nemerosa.ontrack.extension.notifications.channels.NotificationChannelRegistry
import net.nemerosa.ontrack.extension.notifications.channels.NotificationResult
import net.nemerosa.ontrack.extension.notifications.channels.NotificationResultType
import net.nemerosa.ontrack.extension.notifications.http.auth.HttpAuthentication
import net.nemerosa.ontrack.extension.notifications.http.config.HttpConfiguration
import net.nemerosa.ontrack.extension.notifications.http.config.HttpConfigurationService
import net.nemerosa.ontrack.extension.notifications.http.settings.HttpNotificationSettings
import net.nemerosa.ontrack.extension.notifications.recording.NotificationRecordFilter
import net.nemerosa.ontrack.extension.notifications.recording.NotificationRecordingService
import net.nemerosa.ontrack.extension.notifications.subscriptions.subscribe
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.format
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.structure.toProjectEntityID
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The `http` channel against real loopback servers: `MockRestServiceServer` would replace the request
 * factory, which is where the redirects are turned off and the timeouts are set.
 */
@AsAdminTest
class HttpNotificationChannelIT : AbstractNotificationTestSupport() {

    @Autowired
    private lateinit var channel: HttpNotificationChannel

    @Autowired
    private lateinit var notificationChannelRegistry: NotificationChannelRegistry

    @Autowired
    private lateinit var httpConfigurationService: HttpConfigurationService

    @Autowired
    private lateinit var notificationRecordingService: NotificationRecordingService

    private val servers = mutableListOf<HttpServer>()

    @AfterEach
    fun stopServers() {
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

    private fun api(handler: (HttpExchange) -> Unit): HttpServer =
        HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
            createContext("/") { exchange ->
                try {
                    received += Received(
                        method = exchange.requestMethod,
                        path = exchange.requestURI.rawPath,
                        query = exchange.requestURI.rawQuery,
                        headers = exchange.requestHeaders.keys.associate {
                            it.lowercase() to exchange.requestHeaders.getFirst(it)
                        },
                        body = exchange.requestBody.readAllBytes().toString(Charsets.UTF_8),
                    )
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

    private fun HttpExchange.respond(status: Int, body: String? = null, contentType: String? = "application/json") {
        if (contentType != null && body != null) {
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

    private fun configuration(
        url: String,
        authentication: HttpAuthentication = HttpAuthentication.none(),
    ): HttpConfiguration = asAdmin {
        withDisabledConfigurationTest {
            HttpConfiguration(
                name = uid("http-"),
                url = url,
                authentication = authentication,
            ).apply {
                httpConfigurationService.newConfiguration(this)
            }
        }
    }

    private fun publish(config: HttpNotificationChannelConfig): NotificationResult<HttpNotificationChannelOutput> {
        var result: NotificationResult<HttpNotificationChannelOutput>? = null
        project {
            result = channel.publish(
                recordId = "1",
                config = config,
                event = eventFactory.newProject(this),
                context = emptyMap(),
                template = null,
            ) { it }
        }
        return result ?: error("No result")
    }

    // Registration

    @Test
    fun `The channel is registered`() {
        val registered = notificationChannelRegistry.findChannel("http")
        assertNotNull(registered, "The http channel is registered") {
            assertEquals("HTTP call", it.displayName)
            assertTrue(it.enabled, "The channel is enabled")
        }
    }

    // Validation

    private fun validate(config: HttpNotificationChannelConfig) = channel.validate(config.asJson())

    @Test
    fun `A subscription on an existing configuration is valid`() {
        val configuration = configuration("https://api.example.com")
        val validated = validate(
            HttpNotificationChannelConfig(
                config = configuration.name,
                method = HttpCallMethod.POST,
                path = "/deployments/\${build}",
                headers = listOf(HttpNotificationChannelConfigHeader("X-Request-Id", "\${build}")),
                body = mapOf("version" to "\${build}").asJson(),
            )
        )
        assertTrue(validated.isOk(), "Subscription is valid: ${validated.message}")
    }

    @Test
    fun `A subscription on an unknown configuration is rejected`() {
        val unknown = uid("http-")
        val validated = validate(HttpNotificationChannelConfig(config = unknown))
        assertFalse(validated.isOk())
        assertEquals("HTTP configuration $unknown could not be found", validated.message)
    }

    @Test
    fun `A subscription without a configuration is rejected`() {
        val validated = validate(HttpNotificationChannelConfig(config = ""))
        assertFalse(validated.isOk())
        assertEquals("HTTP configuration name is required", validated.message)
    }

    @Test
    fun `A subscription with a body on GET is rejected`() {
        val configuration = configuration("https://api.example.com")
        val validated = validate(
            HttpNotificationChannelConfig(
                config = configuration.name,
                method = HttpCallMethod.GET,
                body = mapOf("a" to "b").asJson(),
            )
        )
        assertFalse(validated.isOk())
        assertEquals("A body cannot be sent with GET, only with POST or PUT.", validated.message)
    }

    @Test
    fun `A subscription setting the Authorization header is rejected`() {
        val configuration = configuration("https://api.example.com")
        val validated = validate(
            HttpNotificationChannelConfig(
                config = configuration.name,
                headers = listOf(HttpNotificationChannelConfigHeader("authorization", "Bearer mine")),
            )
        )
        assertFalse(validated.isOk())
        assertEquals(
            "Header authorization cannot be set by the subscription: it is set by the HTTP configuration only.",
            validated.message
        )
    }

    @Test
    fun `A subscription setting a header of the configuration is rejected`() {
        val configuration = configuration(
            "https://api.example.com",
            HttpAuthentication("header", mapOf("name" to "X-Api-Key", "value" to "secret").asJson()),
        )
        val validated = validate(
            HttpNotificationChannelConfig(
                config = configuration.name,
                headers = listOf(HttpNotificationChannelConfigHeader("x-api-key", "mine")),
            )
        )
        assertFalse(validated.isOk())
    }

    // Calls

    @Test
    fun `GET call`() {
        val server = api { it.respond(200, """{"status":"RUNNING","id":"d-12"}""") }
        val configuration = configuration(url(server))
        val result = publish(
            HttpNotificationChannelConfig(
                config = configuration.name,
                method = HttpCallMethod.GET,
                path = "/deployments/d-12?full=true",
            )
        )
        assertEquals(NotificationResultType.OK, result.type, result.message)
        val request = received.single()
        assertEquals("GET", request.method)
        assertEquals("/api/deployments/d-12", request.path)
        assertEquals("full=true", request.query)
        val output = assertNotNull(result.output)
        assertEquals(HttpCallMethod.GET, output.method)
        assertEquals("${url(server)}/deployments/d-12?full=true", output.url)
        assertEquals(200, output.status)
        assertEquals("""{"status":"RUNNING","id":"d-12"}""".parseAsJson(), output.body)
        assertEquals("application/json", output.headers["content-type"])
        assertFalse(output.truncated)
        assertEquals(1, output.attempts)
    }

    @ParameterizedTest
    @ValueSource(strings = ["POST", "PUT"])
    fun `POST and PUT calls with a templated JSON body`(method: String) {
        val server = api { it.respond(201, """{"id":"d-1"}""") }
        val configuration = configuration(url(server))
        var projectName = ""
        var result: NotificationResult<HttpNotificationChannelOutput>? = null
        project {
            projectName = name
            result = channel.publish(
                recordId = "1",
                config = HttpNotificationChannelConfig(
                    config = configuration.name,
                    method = HttpCallMethod.valueOf(method),
                    path = "/deployments",
                    headers = listOf(HttpNotificationChannelConfigHeader("X-Project", "\${project}")),
                    body = mapOf(
                        "project" to "\${project}",
                        "replicas" to 3,
                        "dryRun" to false,
                    ).asJson(),
                ),
                event = eventFactory.newProject(this),
                context = emptyMap(),
                template = null,
            ) { it }
        }
        assertEquals(NotificationResultType.OK, result?.type, result?.message)
        val request = received.single()
        assertEquals(method, request.method)
        assertEquals("/api/deployments", request.path)
        assertEquals("application/json", request.headers["content-type"])
        assertEquals(projectName, request.headers["x-project"])
        assertEquals(
            mapOf("project" to projectName, "replicas" to 3, "dryRun" to false).asJson(),
            request.body.parseAsJson()
        )
        assertEquals(201, result?.output?.status)
        assertEquals("d-1", result?.output?.body?.path("id")?.asString())
    }

    @ParameterizedTest
    @ValueSource(strings = ["basic", "bearer", "header"])
    fun `The authentication of the configuration is sent, and never recorded`(type: String) {
        val server = api { exchange ->
            exchange.responseHeaders.add("Set-Cookie", "session=cookie-secret")
            exchange.respond(200, """{"ok":true}""")
        }
        val (authentication, expectedHeader) = when (type) {
            "basic" -> HttpAuthentication(
                "basic",
                mapOf("username" to "user", "password" to "the-secret").asJson()
            ) to ("authorization" to "Basic dXNlcjp0aGUtc2VjcmV0")

            "bearer" -> HttpAuthentication(
                "bearer",
                mapOf("token" to "the-secret").asJson()
            ) to ("authorization" to "Bearer the-secret")

            else -> HttpAuthentication(
                "header",
                mapOf("name" to "X-Api-Key", "value" to "the-secret").asJson()
            ) to ("x-api-key" to "the-secret")
        }
        val configuration = configuration(url(server), authentication)
        val result = publish(HttpNotificationChannelConfig(config = configuration.name))
        assertEquals(NotificationResultType.OK, result.type, result.message)
        assertEquals(expectedHeader.second, received.single().headers[expectedHeader.first])

        val recorded = result.asJson().format()
        assertFalse(recorded.contains("the-secret"), "No secret in the result: $recorded")
        assertFalse(recorded.contains("dXNlcjp0aGUtc2VjcmV0"), "No secret in the result: $recorded")
        assertFalse(recorded.contains("cookie-secret"), "No cookie in the result: $recorded")
    }

    @ParameterizedTest
    @ValueSource(ints = [400, 404, 409, 500, 503])
    fun `A non-2xx status is an error`(status: Int) {
        val server = api { it.respond(status, """{"error":"no way"}""") }
        val configuration = configuration(url(server))
        val result = publish(
            HttpNotificationChannelConfig(
                config = configuration.name,
                method = HttpCallMethod.POST,
                path = "/deployments",
            )
        )
        assertEquals(NotificationResultType.ERROR, result.type)
        assertTrue(result.message!!.contains("failed with $status"), "Message names the status: ${result.message}")
        assertEquals(status, result.output?.status)
        assertEquals("no way", result.output?.body?.path("error")?.asString())
    }

    @Test
    fun `A redirect is an error and is not followed`() {
        val elsewhere = api { it.respond(200, """{"ok":true}""") }
        val server = api { exchange ->
            exchange.responseHeaders.set("Location", url(elsewhere) + "/other")
            exchange.respond(302)
        }
        val configuration = configuration(
            url(server),
            HttpAuthentication("bearer", mapOf("token" to "the-secret").asJson()),
        )
        val result = publish(HttpNotificationChannelConfig(config = configuration.name, path = "/deployments"))
        assertEquals(NotificationResultType.ERROR, result.type)
        assertEquals(
            "GET ${url(server)}/deployments was redirected (302 Found) to ${url(elsewhere)}/other, which is not followed.",
            result.message
        )
        assertEquals(302, result.output?.status)
        assertEquals(1, received.size, "Only the first server was called")
    }

    @Test
    fun `A transport error is an error`() {
        val server = api { it.respond(200) }
        val url = url(server)
        server.stop(0)
        val configuration = configuration(url)
        val result = publish(HttpNotificationChannelConfig(config = configuration.name))
        assertEquals(NotificationResultType.ERROR, result.type)
        assertTrue(result.message!!.startsWith("GET $url failed: "), "Message names the call: ${result.message}")
        val output = assertNotNull(result.output)
        assertNull(output.status)
        assertEquals(url, output.url)
        assertEquals(1, output.attempts)
    }

    @Test
    fun `A path leaving the base URL after templating is an invalid configuration`() {
        val server = api { it.respond(200) }
        val configuration = configuration(url(server))
        val result = publish(
            HttpNotificationChannelConfig(
                config = configuration.name,
                // The name of the project is rendered first, then the escape
                path = "/\${project}/../../admin",
            )
        )
        assertEquals(NotificationResultType.INVALID_CONFIGURATION, result.type)
        assertTrue(result.message!!.contains("segment is not accepted"), result.message)
        assertTrue(received.isEmpty(), "No call was made")
    }

    @Test
    fun `A configuration which is gone is an invalid configuration`() {
        val result = publish(HttpNotificationChannelConfig(config = uid("http-")))
        assertEquals(NotificationResultType.INVALID_CONFIGURATION, result.type)
    }

    @Test
    fun `The response body is truncated beyond the maximum response size`() {
        val server = api { it.respond(200, "x".repeat(100), "text/plain") }
        val configuration = configuration(url(server))
        asAdmin {
            withCleanSettings<HttpNotificationSettings> {
                settingsManagerService.saveSettings(HttpNotificationSettings(maxResponseBytes = 10))
                val result = publish(HttpNotificationChannelConfig(config = configuration.name))
                assertEquals(NotificationResultType.OK, result.type, result.message)
                assertEquals(true, result.output?.truncated)
                assertEquals("x".repeat(10), result.output?.body?.asString())
            }
        }
    }

    @Test
    fun `A request longer than the request timeout is an error`() {
        val server = api { exchange ->
            Thread.sleep(2_500)
            exchange.respond(200)
        }
        val configuration = configuration(url(server))
        asAdmin {
            withCleanSettings<HttpNotificationSettings> {
                settingsManagerService.saveSettings(HttpNotificationSettings(requestTimeoutSeconds = 1))
                val result = publish(HttpNotificationChannelConfig(config = configuration.name))
                assertEquals(NotificationResultType.ERROR, result.type)
                assertTrue(result.message!!.contains("timed out", ignoreCase = true), result.message)
            }
        }
    }

    // Through a subscription

    @Test
    fun `A promotion calls the API and records the output`() {
        val server = api { exchange ->
            exchange.responseHeaders.set("Location", "/api/deployments/d-1")
            exchange.respond(201, """{"id":"d-1","status":"RUNNING"}""")
        }
        val configuration = configuration(url(server))
        asAdmin {
            project {
                branch {
                    val pl = promotionLevel()
                    eventSubscriptionService.subscribe(
                        name = uid("s"),
                        channel = channel,
                        channelConfig = HttpNotificationChannelConfig(
                            config = configuration.name,
                            method = HttpCallMethod.POST,
                            path = "/deployments",
                            body = mapOf(
                                "build" to "\${build}",
                                "promotion" to "\${promotionLevel}",
                            ).asJson(),
                        ),
                        projectEntity = pl,
                        keywords = null,
                        origin = "test",
                        contentTemplate = null,
                        EventFactory.NEW_PROMOTION_RUN,
                    )
                    build {
                        val run = promote(pl)
                        assertEquals(
                            mapOf("build" to name, "promotion" to pl.name).asJson(),
                            received.single().body.parseAsJson()
                        )
                        val record = notificationRecordingService.filter(
                            NotificationRecordFilter(eventEntityId = run.toProjectEntityID())
                        ).pageItems.single { it.channel == "http" }
                        assertEquals(NotificationResultType.OK, record.result.type, record.result.message)
                        val output = assertNotNull(record.result.output).parse<HttpNotificationChannelOutput>()
                        assertEquals(201, output.status)
                        assertEquals("d-1", output.body?.path("id")?.asString())
                        assertEquals("/api/deployments/d-1", output.headers["location"])
                    }
                }
            }
        }
    }
}
