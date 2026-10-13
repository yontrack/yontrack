package net.nemerosa.ontrack.kdsl.acceptance.tests.ha

import net.nemerosa.ontrack.kdsl.acceptance.tests.ACCProperties
import net.nemerosa.ontrack.kdsl.acceptance.tests.support.uid
import net.nemerosa.ontrack.kdsl.connector.support.DefaultConnector
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The cross-replica stack itself (#2076): one database behind both backends, and a proxy that
 * reaches both of them.
 */
class ACCHASmoke : AbstractACCHATestSupport() {

    @Test
    fun `a project created through A is read through B`() {
        val name = uid("p_")
        val created = ontrackA.createProject(name, "")
        val read = ontrackB.findProjectByName(name)
        assertNotNull(read, "Project $name created through A is visible through B") {
            assertEquals(created.id, it.id)
        }
    }

    @Test
    fun `calls through the proxy reach both backends`() {
        val client = HttpClient.newHttpClient()
        val upstreams = (1..6).map {
            val request = HttpRequest.newBuilder(URI("${ACCProperties.Connection.url}/graphql"))
                .header("Content-Type", "application/json")
                .header(DefaultConnector.X_ONTRACK_TOKEN, ACCProperties.Connection.token)
                .POST(HttpRequest.BodyPublishers.ofString("""{"query":"{ __typename }"}"""))
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            assertEquals(200, response.statusCode(), "Call through the proxy: ${response.body()}")
            // Set by compose/nginx/kdsl-ha.conf
            response.headers().firstValue("X-Yontrack-Upstream").orElse(null)
                ?: error("The proxy did not say which backend answered")
        }.toSet()
        assertEquals(2, upstreams.size, "Both backends answered through the proxy: $upstreams")
    }

}
