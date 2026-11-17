package net.nemerosa.ontrack.boot.support

import net.nemerosa.ontrack.boot.Application
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Where the mock deployment API is enabled - the `dev` profile of the tests enables it - it answers
 * without any authentication, as the demo's HTTP configuration (`none`) calls it. Nothing else opens up.
 */
@SpringBootTest(
    classes = [Application::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
class MockDeploymentAnonymousIT : AbstractMockDeploymentHttpIT() {

    @Test
    fun `A deployment is started and read without any authentication`() {
        val created = anonymous("POST", "$MOCK_PATH/deployments", """{"durationSeconds": 0}""")
        assertEquals(201, created.statusCode(), "Deployment created: ${created.body()}")
        val node = objectMapper.readTree(created.body())
        assertEquals("RUNNING", node.path("status").asString())

        val location = created.headers().firstValue("Location").orElse(null)
        assertNotNull(location, "Location of the deployment")
        assertEquals("$MOCK_PATH/deployments/${node.path("id").asString()}", location)

        val read = anonymous("GET", location)
        assertEquals(200, read.statusCode(), "Deployment read: ${read.body()}")
        assertEquals("SUCCEEDED", objectMapper.readTree(read.body()).path("status").asString())
    }

    @Test
    fun `The root of the API answers without any authentication`() {
        // What the connection test of an HTTP configuration pointing at it reads
        val response = anonymous("GET", MOCK_PATH)
        assertEquals(200, response.statusCode(), response.body())
        assertEquals("$MOCK_PATH/deployments", objectMapper.readTree(response.body()).path("deployments").asString())
    }

    @Test
    fun `A deployment is started without any body`() {
        val created = anonymous("POST", "$MOCK_PATH/deployments")
        assertEquals(201, created.statusCode(), "Deployment created: ${created.body()}")
    }

    @Test
    fun `An unknown deployment is not found, without any authentication`() {
        assertEquals(404, anonymous("GET", "$MOCK_PATH/deployments/unknown").statusCode())
    }

    @Test
    fun `Nothing else answers without an authentication`() {
        listOf(
            "GET" to "/graphql",
            "POST" to "/graphql",
            "GET" to "/rest/extension/audit-trail/keys",
            "GET" to "/extension/notifications/http",
            "POST" to "/extension/notifications/http/mockx/deployments",
            "POST" to "/extension/notifications/http/mock-deployments",
            "GET" to "/extension/scm/mock/repository/any",
            "GET" to "/extension/notifications/http/mock/../../../../rest/extension/audit-trail/keys",
            "GET" to "/extension/notifications/http/mock/..%2F..%2F..%2F..%2Frest%2Fextension%2Faudit-trail%2Fkeys",
        ).forEach { (method, path) ->
            val status = anonymous(method, path).statusCode()
            assertFalse(status in 200..399, "$method $path is not open: $status")
        }
        // The rest of the API is refused as it always was
        assertEquals(401, anonymous("POST", "/graphql", """{"query":"{ user { account { name } } }"}""").statusCode())
        assertEquals(401, anonymous("GET", "/rest/extension/audit-trail/keys").statusCode())
    }

    @Test
    fun `The instance tells where the backend reaches its own mock deployment API`() {
        val response = graphQL("{ httpMockDeploymentsUrl }")
        assertEquals(200, response.statusCode(), response.body())
        val url = objectMapper.readTree(response.body()).path("data").path("httpMockDeploymentsUrl").asString()
        assertEquals("http://localhost:$port$MOCK_PATH", url)
        // ... and the backend can call it there, anonymously
        val created = anonymous("POST", "${url.removePrefix("http://localhost:$port")}/deployments")
        assertTrue(created.statusCode() == 201, "Deployment created at $url: ${created.body()}")
    }
}
