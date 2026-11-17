package net.nemerosa.ontrack.boot.support

import net.nemerosa.ontrack.boot.Application
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Where the mock deployment API is disabled, its paths are like any unknown path of the API: refused
 * without an authentication, not found with one. The query telling where it is does not exist.
 */
@SpringBootTest(
    classes = [Application::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "ontrack.config.extension.notifications.http.mock.enabled=false",
    ],
)
class MockDeploymentDisabledIT : AbstractMockDeploymentHttpIT() {

    @Test
    fun `The mock deployment API is refused without an authentication`() {
        assertEquals(401, anonymous("POST", "$MOCK_PATH/deployments", "{}").statusCode())
        assertEquals(401, anonymous("GET", "$MOCK_PATH/deployments/any").statusCode())
    }

    @Test
    fun `The mock deployment API is not found with an authentication`() {
        assertEquals(404, authenticated("POST", "$MOCK_PATH/deployments", "{}").statusCode())
    }

    @Test
    fun `The instance does not tell where the mock deployment API is`() {
        val response = graphQL("{ httpMockDeploymentsUrl }")
        val errors = objectMapper.readTree(response.body()).path("errors")
        assertTrue(
            errors.isArray && errors.size() > 0,
            "The query is refused: ${response.body()}",
        )
    }
}
