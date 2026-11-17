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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import java.net.InetAddress
import java.net.InetSocketAddress
import java.time.Duration
import java.time.Instant
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

    /**
     * Polling without waiting: each sleep moves the time forward at once.
     */
    private class FakeClock : HttpCallPollingClock {
        var now: Instant = Instant.parse("2026-11-17T10:00:00Z")
        val sleeps = mutableListOf<Duration>()
        override fun now(): Instant = now
        override fun sleep(duration: Duration) {
            sleeps += duration
            now = now.plus(duration)
        }
    }

    private val clock = FakeClock()

    @BeforeEach
    fun fakeClock() {
        channel.pollingClock = clock
    }

    @AfterEach
    fun systemClock() {
        channel.pollingClock = HttpCallPollingClock.SYSTEM
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

    private fun publish(
        config: HttpNotificationChannelConfig,
        progress: (HttpNotificationChannelOutput) -> Unit = {},
    ): NotificationResult<HttpNotificationChannelOutput> {
        var result: NotificationResult<HttpNotificationChannelOutput>? = null
        project {
            result = channel.publish(
                recordId = "1",
                config = config,
                event = eventFactory.newProject(this),
                context = emptyMap(),
                template = null,
            ) {
                progress(it)
                it
            }
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

    // Conditions and polling

    /**
     * API answering the given responses, one per attempt, the last one forever.
     *
     * The attempt is told by the number of waits so far, not by the number of requests received: the HTTP
     * client may retry a `GET` whose connection was closed, within the same attempt.
     */
    private fun scripted(vararg responses: (HttpExchange) -> Unit): HttpServer =
        api { exchange ->
            responses[minOf(clock.sleeps.size, responses.size - 1)](exchange)
        }

    private fun deployment(status: String): (HttpExchange) -> Unit = {
        it.respond(200, """{"id":"d-1","status":"$status"}""")
    }

    private fun failing(status: Int): (HttpExchange) -> Unit = {
        it.respond(status, """{"error":"busy"}""")
    }

    private val succeeded = "response.body.status == 'SUCCEEDED'"
    private val failed = "response.body.status == 'FAILED'"

    @Test
    fun `A subscription with invalid conditions is rejected, naming the expression`() {
        val configuration = configuration("https://api.example.com")
        val validated = validate(
            HttpNotificationChannelConfig(
                config = configuration.name,
                successCondition = "response.body.status ==",
            )
        )
        assertFalse(validated.isOk())
        assertTrue(
            validated.message!!.startsWith("Success condition `response.body.status ==` is not valid: "),
            validated.message
        )
    }

    @Test
    fun `A subscription with a too short interval is rejected`() {
        val configuration = configuration("https://api.example.com")
        val validated = validate(
            HttpNotificationChannelConfig(
                config = configuration.name,
                successCondition = succeeded,
                intervalSeconds = 1,
            )
        )
        assertFalse(validated.isOk())
        assertEquals("The interval must be at least 5 seconds.", validated.message)
    }

    @Test
    fun `A subscription with all conditions is valid`() {
        val configuration = configuration("https://api.example.com")
        val validated = validate(
            HttpNotificationChannelConfig(
                config = configuration.name,
                method = HttpCallMethod.POST,
                path = "/deployments",
                successCondition = succeeded,
                failureCondition = failed,
                statusUrl = "response.headers['location']",
                intervalSeconds = 5,
                timeoutSeconds = 600,
            )
        )
        assertTrue(validated.isOk(), validated.message)
    }

    @Test
    fun `Success on the initial response`() {
        val server = scripted(deployment("SUCCEEDED"))
        val configuration = configuration(url(server))
        val result = publish(
            HttpNotificationChannelConfig(
                config = configuration.name,
                path = "/deployments/d-1",
                successCondition = succeeded,
            )
        )
        assertEquals(NotificationResultType.OK, result.type, result.message)
        val output = assertNotNull(result.output)
        assertEquals(1, output.attempts)
        assertEquals(HttpCallConditionOutcome.SUCCESS, output.condition)
        assertNull(output.statusUrl, "Nothing was polled")
        assertEquals(1, received.size)
        assertTrue(clock.sleeps.isEmpty())
    }

    @Test
    fun `Failure on the initial response`() {
        val server = scripted(deployment("FAILED"))
        val configuration = configuration(url(server))
        val result = publish(
            HttpNotificationChannelConfig(
                config = configuration.name,
                method = HttpCallMethod.POST,
                path = "/deployments",
                successCondition = succeeded,
                failureCondition = failed,
            )
        )
        assertEquals(NotificationResultType.ERROR, result.type)
        assertEquals(
            "POST ${url(server)}/deployments: the failure condition `$failed` holds.",
            result.message
        )
        assertEquals(HttpCallConditionOutcome.FAILURE, result.output?.condition)
    }

    @Test
    fun `A GET is polled on its own URL until it succeeds, recording its progress`() {
        val server = scripted(deployment("RUNNING"), deployment("RUNNING"), deployment("SUCCEEDED"))
        val configuration = configuration(url(server))
        val progress = mutableListOf<HttpNotificationChannelOutput>()
        val result = publish(
            HttpNotificationChannelConfig(
                config = configuration.name,
                path = "/deployments/d-1",
                successCondition = succeeded,
                failureCondition = failed,
                intervalSeconds = 7,
            ),
            progress = { progress += it },
        )
        assertEquals(NotificationResultType.OK, result.type, result.message)
        assertEquals(listOf("/api/deployments/d-1"), received.map { it.path }.distinct())
        assertEquals(listOf("GET"), received.map { it.method }.distinct())
        assertEquals(3, received.size)
        assertEquals(List(2) { Duration.ofSeconds(7) }, clock.sleeps)
        val output = assertNotNull(result.output)
        assertEquals(3, output.attempts)
        assertEquals(HttpCallConditionOutcome.SUCCESS, output.condition)
        assertEquals("${url(server)}/deployments/d-1", output.statusUrl)
        assertEquals("SUCCEEDED", output.body?.path("status")?.asString())
        // Progress pushed after each poll
        val polls = progress.filter { it.condition != null }.map { it.attempts to it.condition }
        assertEquals(
            listOf(
                2 to HttpCallConditionOutcome.PENDING,
                3 to HttpCallConditionOutcome.SUCCESS,
            ),
            polls.filter { it.first > 1 }
        )
    }

    @Test
    fun `Polling until the failure condition holds`() {
        val server = scripted(deployment("RUNNING"), deployment("RUNNING"), deployment("FAILED"))
        val configuration = configuration(url(server))
        val result = publish(
            HttpNotificationChannelConfig(
                config = configuration.name,
                path = "/deployments/d-1",
                successCondition = succeeded,
                failureCondition = failed,
            )
        )
        assertEquals(NotificationResultType.ERROR, result.type)
        assertEquals(
            "GET ${url(server)}/deployments/d-1: the failure condition `$failed` holds.",
            result.message
        )
        assertEquals(3, result.output?.attempts)
        assertEquals(HttpCallConditionOutcome.FAILURE, result.output?.condition)
    }

    @Test
    fun `Still pending at the timeout is an error`() {
        val server = scripted(deployment("RUNNING"))
        val configuration = configuration(url(server))
        val result = publish(
            HttpNotificationChannelConfig(
                config = configuration.name,
                path = "/deployments/d-1",
                successCondition = succeeded,
                intervalSeconds = 5,
                timeoutSeconds = 20,
            )
        )
        assertEquals(NotificationResultType.ERROR, result.type)
        assertEquals(
            "GET ${url(server)}/deployments/d-1: still pending, timed out after 20 s (polling GET ${url(server)}/deployments/d-1).",
            result.message
        )
        // The initial request, and one poll every 5 s for 20 s
        assertEquals(5, received.size)
        assertEquals(5, result.output?.attempts)
        assertEquals(HttpCallConditionOutcome.PENDING, result.output?.condition)
    }

    @Test
    fun `The timeout is capped by the settings`() {
        val server = scripted(deployment("RUNNING"))
        val configuration = configuration(url(server))
        asAdmin {
            withCleanSettings<HttpNotificationSettings> {
                settingsManagerService.saveSettings(HttpNotificationSettings(maxTimeoutSeconds = 10))
                val result = publish(
                    HttpNotificationChannelConfig(
                        config = configuration.name,
                        path = "/deployments/d-1",
                        successCondition = succeeded,
                        intervalSeconds = 5,
                        timeoutSeconds = 300,
                    )
                )
                assertEquals(NotificationResultType.ERROR, result.type)
                assertTrue(result.message!!.contains("timed out after 10 s"), result.message)
                assertEquals(3, received.size)
            }
        }
    }

    @Test
    fun `POST polled on the status URL of its Location header`() {
        val server = api { exchange ->
            if (exchange.requestMethod == "POST") {
                exchange.responseHeaders.set("Location", "/api/deployments/d-1")
                exchange.respond(201, """{"id":"d-1","status":"RUNNING"}""")
            } else if (received.count { it.method == "GET" } < 2) {
                deployment("RUNNING")(exchange)
            } else {
                deployment("SUCCEEDED")(exchange)
            }
        }
        val configuration = configuration(url(server))
        val result = publish(
            HttpNotificationChannelConfig(
                config = configuration.name,
                method = HttpCallMethod.POST,
                path = "/deployments",
                body = mapOf("version" to "1.0").asJson(),
                successCondition = succeeded,
                failureCondition = failed,
                statusUrl = "response.headers['location']",
            )
        )
        assertEquals(NotificationResultType.OK, result.type, result.message)
        assertEquals(
            listOf("POST /api/deployments", "GET /api/deployments/d-1", "GET /api/deployments/d-1"),
            received.map { "${it.method} ${it.path}" }
        )
        assertEquals("", received.last().body, "No body sent on a poll")
        val output = assertNotNull(result.output)
        assertEquals(HttpCallMethod.POST, output.method)
        assertEquals("${url(server)}/deployments", output.url)
        assertEquals("${url(server)}/deployments/d-1", output.statusUrl)
        assertEquals(3, output.attempts)
    }

    @Test
    fun `PUT polled on the status URL built from its body`() {
        val server = api { exchange ->
            if (exchange.requestMethod == "PUT") {
                exchange.respond(202, """{"id":"d-2","status":"RUNNING"}""")
            } else {
                deployment("SUCCEEDED")(exchange)
            }
        }
        val configuration = configuration(url(server))
        val result = publish(
            HttpNotificationChannelConfig(
                config = configuration.name,
                method = HttpCallMethod.PUT,
                path = "/deployments",
                successCondition = succeeded,
                statusUrl = "'/deployments/' + response.body.id + '/status'",
            )
        )
        assertEquals(NotificationResultType.OK, result.type, result.message)
        assertEquals(
            listOf("PUT /api/deployments", "GET /api/deployments/d-2/status"),
            received.map { "${it.method} ${it.path}" }
        )
    }

    @Test
    fun `A status URL outside the base URL is refused`() {
        val elsewhere = api { deployment("SUCCEEDED")(it) }
        val server = api { exchange ->
            exchange.responseHeaders.set("Location", "${url(elsewhere)}/deployments/d-1")
            exchange.respond(201, """{"id":"d-1","status":"RUNNING"}""")
        }
        val configuration = configuration(url(server))
        val result = publish(
            HttpNotificationChannelConfig(
                config = configuration.name,
                method = HttpCallMethod.POST,
                path = "/deployments",
                successCondition = succeeded,
                statusUrl = "response.headers['location']",
            )
        )
        assertEquals(NotificationResultType.ERROR, result.type)
        assertTrue(result.message!!.contains("the status URL cannot be polled"), result.message)
        assertTrue(result.message!!.contains("it is outside the base URL"), result.message)
        assertEquals(1, received.size, "Nothing was polled")
    }

    @Test
    fun `A POST still pending without a status URL is an error`() {
        val server = scripted(deployment("RUNNING"))
        val configuration = configuration(url(server))
        val result = publish(
            HttpNotificationChannelConfig(
                config = configuration.name,
                method = HttpCallMethod.POST,
                path = "/deployments",
                successCondition = succeeded,
            )
        )
        assertEquals(NotificationResultType.ERROR, result.type)
        assertEquals(
            "POST ${url(server)}/deployments: the success condition `$succeeded` does not hold on the response, and there is no status URL to poll.",
            result.message
        )
        assertEquals(1, received.size)
        assertEquals(HttpCallConditionOutcome.PENDING, result.output?.condition)
    }

    @Test
    fun `A non-JSON body with conditions is an error`() {
        val server = api { it.respond(200, "<html/>", "text/html") }
        val configuration = configuration(url(server))
        val result = publish(
            HttpNotificationChannelConfig(
                config = configuration.name,
                successCondition = succeeded,
            )
        )
        assertEquals(NotificationResultType.ERROR, result.type)
        assertEquals(
            "GET ${url(server)}: The response body is not JSON (text/html), conditions cannot be evaluated.",
            result.message
        )
    }

    @Test
    fun `A non-2xx initial response is an error, whatever the conditions`() {
        val server = scripted(failing(503))
        val configuration = configuration(url(server))
        val result = publish(
            HttpNotificationChannelConfig(
                config = configuration.name,
                successCondition = "response.status == 503",
            )
        )
        assertEquals(NotificationResultType.ERROR, result.type)
        assertEquals("GET ${url(server)} failed with 503 Service Unavailable.", result.message)
        assertEquals(1, received.size)
    }

    @Test
    fun `Up to 3 consecutive 5xx and 429 are tolerated while polling`() {
        val server = scripted(
            deployment("RUNNING"),
            failing(500),
            failing(429),
            failing(503),
            deployment("RUNNING"),
            failing(502),
            failing(504),
            failing(500),
            deployment("SUCCEEDED"),
        )
        val configuration = configuration(url(server))
        val result = publish(
            HttpNotificationChannelConfig(
                config = configuration.name,
                successCondition = succeeded,
            )
        )
        assertEquals(NotificationResultType.OK, result.type, result.message)
        assertEquals(9, result.output?.attempts)
    }

    @Test
    fun `A 4th consecutive 5xx while polling is an error`() {
        val server = scripted(deployment("RUNNING"), failing(503))
        val configuration = configuration(url(server))
        val result = publish(
            HttpNotificationChannelConfig(
                config = configuration.name,
                successCondition = succeeded,
            )
        )
        assertEquals(NotificationResultType.ERROR, result.type)
        assertEquals(
            "GET ${url(server)} failed with 503 Service Unavailable 4 times in a row.",
            result.message
        )
        assertEquals(5, received.size)
        assertEquals(503, result.output?.status)
    }

    @Test
    fun `Transport errors while polling are tolerated up to 3 in a row`() {
        // The connection is closed without any response
        val dropped: (HttpExchange) -> Unit = { exchange -> exchange.close() }
        val server = scripted(deployment("RUNNING"), dropped, dropped, dropped, deployment("SUCCEEDED"))
        val configuration = configuration(url(server))
        val result = publish(
            HttpNotificationChannelConfig(
                config = configuration.name,
                successCondition = succeeded,
            )
        )
        assertEquals(NotificationResultType.OK, result.type, result.message)
        assertEquals(5, result.output?.attempts)
    }

    @Test
    fun `A 4th consecutive transport error while polling is an error`() {
        val dropped: (HttpExchange) -> Unit = { exchange -> exchange.close() }
        val server = scripted(deployment("RUNNING"), dropped)
        val configuration = configuration(url(server))
        val result = publish(
            HttpNotificationChannelConfig(
                config = configuration.name,
                successCondition = succeeded,
            )
        )
        assertEquals(NotificationResultType.ERROR, result.type)
        assertTrue(
            result.message!!.startsWith("GET ${url(server)} failed 4 times in a row: "),
            result.message
        )
        assertNull(result.output?.status)
        assertEquals(5, result.output?.attempts)
    }

    @ParameterizedTest
    @ValueSource(ints = [400, 401, 403, 404, 410])
    fun `A 4xx while polling is an error at once`(status: Int) {
        val server = scripted(deployment("RUNNING"), failing(status))
        val configuration = configuration(url(server))
        val result = publish(
            HttpNotificationChannelConfig(
                config = configuration.name,
                successCondition = succeeded,
            )
        )
        assertEquals(NotificationResultType.ERROR, result.type)
        assertTrue(result.message!!.startsWith("GET ${url(server)} failed with $status"), result.message)
        assertEquals(2, received.size)
    }

    @Test
    fun `The attempt is available to the conditions`() {
        val server = scripted(deployment("RUNNING"))
        val configuration = configuration(url(server))
        val result = publish(
            HttpNotificationChannelConfig(
                config = configuration.name,
                successCondition = "attempt == 3",
            )
        )
        assertEquals(NotificationResultType.OK, result.type, result.message)
        assertEquals(3, received.size)
    }

    @Test
    fun `A promotion waits for the deployment and records the output`() {
        val server = api { exchange ->
            if (exchange.requestMethod == "POST") {
                exchange.responseHeaders.set("Location", "${url(exchange.httpContext.server as HttpServer)}/deployments/d-1")
                exchange.respond(201, """{"id":"d-1","status":"RUNNING"}""")
            } else {
                deployment("SUCCEEDED")(exchange)
            }
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
                            body = mapOf("build" to "\${build}").asJson(),
                            successCondition = succeeded,
                            failureCondition = failed,
                            statusUrl = "response.headers['location']",
                        ),
                        projectEntity = pl,
                        keywords = null,
                        origin = "test",
                        contentTemplate = null,
                        EventFactory.NEW_PROMOTION_RUN,
                    )
                    build {
                        val run = promote(pl)
                        val record = notificationRecordingService.filter(
                            NotificationRecordFilter(eventEntityId = run.toProjectEntityID())
                        ).pageItems.single { it.channel == "http" }
                        assertEquals(NotificationResultType.OK, record.result.type, record.result.message)
                        val output = assertNotNull(record.result.output).parse<HttpNotificationChannelOutput>()
                        assertEquals(2, output.attempts)
                        assertEquals("${url(server)}/deployments/d-1", output.statusUrl)
                        assertEquals(HttpCallConditionOutcome.SUCCESS, output.condition)
                        assertEquals("SUCCEEDED", output.body?.path("status")?.asString())
                    }
                }
            }
        }
    }
}
