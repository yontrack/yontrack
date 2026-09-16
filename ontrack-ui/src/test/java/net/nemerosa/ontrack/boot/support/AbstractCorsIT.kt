package net.nemerosa.ontrack.boot.support

import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.model.structure.TokenOptions
import net.nemerosa.ontrack.model.structure.TokensConstants
import net.nemerosa.ontrack.model.structure.TokensService
import net.nemerosa.ontrack.test.TestUtils.uid
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * CORS as a browser sees it: a preflight sent over HTTP to a running instance, through the
 * whole filter chain, Spring Security included (#1771).
 *
 * A browser only lets a cross-origin call through when the answer carries an
 * `Access-Control-Allow-Origin` naming the calling origin, so that header is what is asserted -
 * on the preflight, and on the actual, authenticated call, which is what a scanner sees when it
 * sends an `Origin` along with its token.
 *
 * Not transactional, unlike the other ITs: the token the calls authenticate with must be committed
 * for the server, on its own threads, to find it.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
abstract class AbstractCorsIT : AbstractDSLTestSupport() {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var tokensService: TokensService

    private val client: HttpClient = HttpClient.newHttpClient()

    private val token: String by lazy {
        asAdmin().call {
            tokensService.generateNewToken(TokenOptions(name = uid("cors-"))).value
        }
    }

    /**
     * An authenticated call carrying an `Origin`: a GET, or a POST of a GraphQL query.
     */
    protected fun call(path: String, origin: String): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://localhost:$port$path"))
            .header("Origin", origin)
            .header(TokensConstants.HTTP_ONTRACK_TOKEN, token)
            .header("Accept", "application/json")
        if (path == GRAPHQL) {
            builder
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""{"query":"{ user { account { name } } }"}"""))
        } else {
            builder.GET()
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    protected fun assertCallRefused(path: String, origin: String) {
        val response = call(path, origin)
        assertNull(
            response.headers().firstValue("Access-Control-Allow-Origin").orElse(null),
            "Call on $path from $origin is not allowed to be read (status ${response.statusCode()})",
        )
    }

    protected fun assertCallAccepted(path: String, origin: String) {
        val response = call(path, origin)
        assertEquals(200, response.statusCode(), "Call on $path from $origin answers 200: ${response.body()}")
        assertEquals(
            origin,
            response.headers().firstValue("Access-Control-Allow-Origin").orElse(null),
            "Call on $path from $origin names that origin",
        )
    }

    protected fun preflight(path: String, origin: String, method: String = "POST"): HttpResponse<String> =
        client.send(
            HttpRequest.newBuilder(URI("http://localhost:$port$path"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", origin)
                .header("Access-Control-Request-Method", method)
                .header("Access-Control-Request-Headers", "authorization,content-type")
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    protected fun assertRefused(path: String, origin: String, method: String = "POST") {
        val response = preflight(path, origin, method)
        assertNull(
            response.headers().firstValue("Access-Control-Allow-Origin").orElse(null),
            "Preflight on $path from $origin is refused (status ${response.statusCode()})",
        )
    }

    protected fun assertAccepted(path: String, origin: String, method: String = "POST") {
        val response = preflight(path, origin, method)
        assertEquals(200, response.statusCode(), "Preflight on $path from $origin answers 200")
        assertEquals(
            origin,
            response.headers().firstValue("Access-Control-Allow-Origin").orElse(null),
            "Preflight on $path from $origin names that origin",
        )
        val methods = response.headers().firstValue("Access-Control-Allow-Methods").orElse("")
        assertTrue(method in methods.split(",").map { it.trim() }, "$method is allowed on $path: $methods")
    }

    companion object {
        const val FOREIGN_ORIGIN = "https://foreign.example"
        const val ALLOWED_ORIGIN = "https://allowed.example"
        const val OTHER_ALLOWED_ORIGIN = "https://other.example"

        const val GRAPHQL = "/graphql"
        const val REST = "/rest/info"
        const val EXTENSION = "/extension/license"
        const val HOOK = "/hook/secured/github"
    }
}
