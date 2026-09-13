package net.nemerosa.ontrack.boot.ui

import net.nemerosa.ontrack.boot.Application
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalManagementPort
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The management port is permitAll (see `WebSecurityConfig`): nothing but the shipped
 * defaults decides what it gives away to whoever can reach it (#1772).
 *
 * The test runs on the `dev` profile like every IT, so `application-dev.yml` must not widen
 * the exposure again - the tooling which needs more endpoints asks for them explicitly.
 */
@SpringBootTest(
    classes = [Application::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "management.server.port=0",
    ],
)
class ManagementEndpointsDefaultsIT : AbstractDSLTestSupport() {

    @LocalManagementPort
    private var managementPort: Int = 0

    private val client: HttpClient = HttpClient.newHttpClient()

    private fun get(path: String): HttpResponse<String> =
        client.send(
            HttpRequest.newBuilder(URI("http://localhost:$managementPort/manage$path")).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    @Test
    fun `Health is exposed without any details`() {
        val response = get("/health")
        // UP or DOWN depending on the middleware of the run, but never an error
        assertTrue(response.statusCode() in setOf(200, 503), "Health answers: ${response.statusCode()}")
        val node = objectMapper.readTree(response.body())
        assertTrue(node.has("status"), "Health has a status")
        assertFalse(node.has("components"), "Health has no components")
        assertFalse(node.has("details"), "Health has no details")
    }

    @Test
    fun `Info and Prometheus are exposed`() {
        assertEquals(200, get("/info").statusCode())
        assertEquals(200, get("/prometheus").statusCode())
    }

    @Test
    fun `Endpoints not in the default list are not exposed`() {
        listOf(
            "/env",
            "/configprops",
            "/beans",
            "/metrics",
            "/loggers",
            "/heapdump",
            "/threaddump",
            "/mappings",
            "/graphql",
            "/graphqlJson",
            "/account/admin",
        ).forEach { path ->
            assertEquals(404, get(path).statusCode(), "$path is not exposed")
        }
    }
}
