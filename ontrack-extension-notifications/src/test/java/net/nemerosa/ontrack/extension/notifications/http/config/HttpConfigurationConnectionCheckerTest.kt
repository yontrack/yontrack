package net.nemerosa.ontrack.extension.notifications.http.config

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import net.nemerosa.ontrack.extension.notifications.http.auth.HttpAuthentication
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.support.ConnectionResultType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Connection test of an HTTP configuration, over a real socket: `MockRestServiceServer` replaces the
 * request factory, which is where the redirects are turned off.
 */
class HttpConfigurationConnectionCheckerTest {

    private val servers = mutableListOf<HttpServer>()

    private val checker = HttpConfigurationConnectionChecker()

    @AfterEach
    fun after() {
        servers.forEach { it.stop(0) }
    }

    @Test
    fun `GET on the base URL with the authentication`() {
        val requests = CopyOnWriteArrayList<Pair<String, String?>>()
        val api = server { exchange ->
            requests += exchange.requestMethod to exchange.requestHeaders.getFirst("Authorization")
            exchange.respond(200)
        }
        val result = checker.check(
            HttpConfiguration(
                name = "api",
                url = url(api),
                authentication = HttpAuthentication(
                    type = "bearer",
                    config = mapOf("token" to "secret").asJson(),
                ),
            )
        )
        assertEquals(ConnectionResultType.OK, result.type)
        assertEquals(listOf("GET" to "Bearer secret"), requests.toList())
    }

    @ParameterizedTest
    @ValueSource(ints = [200, 204, 400, 404, 405])
    fun `Statuses accepted by the connection test`(status: Int) {
        val api = server { it.respond(status) }
        val result = checker.check(HttpConfiguration(name = "api", url = url(api)))
        assertEquals(ConnectionResultType.OK, result.type, result.message)
    }

    @ParameterizedTest
    @ValueSource(ints = [401, 403, 500, 502, 503])
    fun `Statuses refused by the connection test`(status: Int) {
        val api = server { it.respond(status) }
        val result = checker.check(HttpConfiguration(name = "api", url = url(api)))
        assertEquals(ConnectionResultType.ERROR, result.type)
        assertTrue(result.message.contains(status.toString()), "The message names the status: ${result.message}")
    }

    @Test
    fun `A redirect is an error and is not followed`() {
        val reachedElsewhere = CopyOnWriteArrayList<String?>()
        val elsewhere = server { exchange ->
            reachedElsewhere += exchange.requestHeaders.getFirst("Authorization")
            exchange.respond(200)
        }
        val api = server { exchange ->
            exchange.responseHeaders.set("Location", url(elsewhere))
            exchange.respond(302)
        }
        val result = checker.check(
            HttpConfiguration(
                name = "api",
                url = url(api),
                authentication = HttpAuthentication(
                    type = "bearer",
                    config = mapOf("token" to "secret").asJson(),
                ),
            )
        )
        assertEquals(ConnectionResultType.ERROR, result.type)
        assertTrue(result.message.contains("302"), "The message names the status: ${result.message}")
        assertEquals(emptyList(), reachedElsewhere.toList(), "The redirect was not followed")
    }

    @Test
    fun `A transport error is an error`() {
        val api = server { it.respond(200) }
        val url = url(api)
        api.stop(0)
        val result = checker.check(HttpConfiguration(name = "api", url = url))
        assertEquals(ConnectionResultType.ERROR, result.type)
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

    private fun HttpExchange.respond(status: Int) {
        sendResponseHeaders(status, -1L)
    }
}
