package net.nemerosa.ontrack.extension.gitlab.client

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import net.nemerosa.ontrack.extension.gitlab.model.GitLabConfiguration
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * That the client does not follow a redirect, over a real socket.
 *
 * `MockRestServiceServer` cannot answer this: it replaces the request factory, which is the very thing under
 * test. Left to itself `SimpleClientHttpRequestFactory` turns `instanceFollowRedirects` on for a GET, and
 * `HttpURLConnection` replays the request properties - the `PRIVATE-TOKEN` header among them - onto the
 * redirected request. A 302 would then hand the personal access token to whatever host the `Location` names,
 * which is the same exposure as a cross-host `Link` header by another route.
 *
 * See the `java/ssrf` alert https://github.com/yontrack/yontrack/security/code-scanning/355.
 */
class DefaultGitLabClientRedirectTest {

    /**
     * Stands in for the GitLab instance the configuration names, and answers every call with a redirect.
     */
    private lateinit var instance: HttpServer

    /**
     * Stands in for wherever the redirect points. It records what reaches it - nothing, if the client
     * behaves.
     */
    private lateinit var elsewhere: HttpServer

    private val reachedElsewhere = CopyOnWriteArrayList<String?>()

    @BeforeEach
    fun before() {
        elsewhere = server { exchange ->
            reachedElsewhere += exchange.requestHeaders.getFirst(DefaultGitLabClient.PRIVATE_TOKEN_HEADER)
            exchange.respond(200, """{"id":1,"username":"bot"}""")
        }
        instance = server { exchange ->
            exchange.responseHeaders.set("Location", "${url(elsewhere)}/api/v4/user")
            exchange.respond(302, "")
        }
    }

    @AfterEach
    fun after() {
        instance.stop(0)
        elsewhere.stop(0)
    }

    @Test
    fun `A redirect off the instance is refused rather than followed`() {
        val client = DefaultGitLabClient(
            GitLabConfiguration(name = "gl", url = url(instance), token = "secret")
        )
        val ex = assertThrows<GitLabRedirectException> {
            client.validate()
        }
        assertEquals(
            emptyList(),
            reachedElsewhere,
            "The redirect target was never called, so the token never left the instance",
        )
        assertTrue(
            ex.message!!.contains("redirected"),
            "The error names the redirect rather than an empty answer: ${ex.message}",
        )
    }

    @Test
    fun `A redirect is refused the same way when the certificate is ignored`() {
        // `ignoreSslCertificate` takes a different branch of the request factory, and that branch must
        // disable the redirects too.
        val client = DefaultGitLabClient(
            GitLabConfiguration(
                name = "gl",
                url = url(instance),
                token = "secret",
                ignoreSslCertificate = true,
            )
        )
        assertThrows<GitLabRedirectException> {
            client.validate()
        }
        assertEquals(emptyList(), reachedElsewhere)
    }

    private fun server(handler: (HttpExchange) -> Unit): HttpServer =
        HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
            createContext("/") { exchange ->
                exchange.use { handler(it) }
            }
            start()
        }

    private fun url(server: HttpServer): String =
        "http://${server.address.hostString}:${server.address.port}"

    private fun HttpExchange.respond(status: Int, body: String) {
        val bytes = body.toByteArray()
        responseHeaders.set("Content-Type", "application/json")
        sendResponseHeaders(status, if (bytes.isEmpty()) -1L else bytes.size.toLong())
        if (bytes.isNotEmpty()) {
            responseBody.write(bytes)
        }
    }

    private inline fun HttpExchange.use(block: (HttpExchange) -> Unit) {
        try {
            block(this)
        } finally {
            close()
        }
    }
}
