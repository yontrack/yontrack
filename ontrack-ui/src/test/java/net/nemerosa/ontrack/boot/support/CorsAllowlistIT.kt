package net.nemerosa.ontrack.boot.support

import net.nemerosa.ontrack.boot.Application
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

/**
 * A deployment which really needs a browser to call the API from another origin lists that
 * origin in `ontrack.config.security.cors.allowed-origins`, and only that origin (#1771).
 */
@SpringBootTest(
    classes = [Application::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "ontrack.config.security.cors.allowed-origins=${AbstractCorsIT.ALLOWED_ORIGIN},${AbstractCorsIT.OTHER_ALLOWED_ORIGIN}",
    ],
)
class CorsAllowlistIT : AbstractCorsIT() {

    @Test
    fun `GraphQL accepts a listed origin`() {
        assertAccepted(GRAPHQL, ALLOWED_ORIGIN)
        assertAccepted(GRAPHQL, OTHER_ALLOWED_ORIGIN)
        assertAccepted(GRAPHQL, ALLOWED_ORIGIN, method = "GET")
        assertCallAccepted(GRAPHQL, ALLOWED_ORIGIN)
    }

    @Test
    fun `GraphQL still refuses an origin which is not listed`() {
        assertRefused(GRAPHQL, FOREIGN_ORIGIN)
        assertCallRefused(GRAPHQL, FOREIGN_ORIGIN)
    }

    @Test
    fun `REST accepts a listed origin and refuses the others`() {
        assertAccepted(REST, ALLOWED_ORIGIN, method = "GET")
        assertCallAccepted(REST, ALLOWED_ORIGIN)
        assertRefused(REST, FOREIGN_ORIGIN, method = "GET")
        assertCallRefused(REST, FOREIGN_ORIGIN)
    }

    @Test
    fun `Extension endpoints accept a listed origin and refuse the others`() {
        assertAccepted(EXTENSION, ALLOWED_ORIGIN, method = "GET")
        assertCallAccepted(EXTENSION, ALLOWED_ORIGIN)
        assertRefused(EXTENSION, FOREIGN_ORIGIN, method = "GET")
        assertCallRefused(EXTENSION, FOREIGN_ORIGIN)
    }

    @Test
    fun `Hooks have no CORS, not even for a listed origin`() {
        assertRefused(HOOK, ALLOWED_ORIGIN)
    }
}
