package net.nemerosa.ontrack.boot.support

import net.nemerosa.ontrack.boot.Application
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.structure.TokenOptions
import net.nemerosa.ontrack.model.structure.TokensConstants
import net.nemerosa.ontrack.model.structure.TokensService
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The GraphQL answer, as a client reads it over HTTP.
 *
 * The values of the `JSON` scalar are Jackson 2 nodes, and the HTTP layer must write them with
 * Jackson 2: Spring Boot 4 brings Jackson 3 along, and Jackson 3 would write such a node as a bean
 * -- `{"array":false,"containerNode":true,...}` -- instead of as its content.
 *
 * Not transactional: the token the call authenticates with must be committed for the server, on
 * its own threads, to find it.
 */
@SpringBootTest(
    classes = [Application::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GraphQLHttpJsonIT : AbstractDSLTestSupport() {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var tokensService: TokensService

    @Test
    fun `JSON scalars are written as their content`() {
        val token = asAdmin().call {
            tokensService.generateNewToken(TokenOptions(name = uid("graphql-"))).value
        }
        val response = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI("http://localhost:$port/graphql"))
                .header(TokensConstants.HTTP_ONTRACK_TOKEN, token)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""{"query":"{ systemHealth { health } }"}"""))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        assertEquals(200, response.statusCode(), response.body())

        val health = response.body().parseAsJson().path("data").path("systemHealth").path("health")
        assertTrue(health.path("status").isTextual, "The health has a textual status: $health")
        assertFalse(health.has("containerNode"), "The health is not written as a Jackson node bean: $health")
    }

}
