package net.nemerosa.ontrack.boot.support

import net.nemerosa.ontrack.boot.Application
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.assertEquals

/**
 * Out of the box, the API answers no cross-origin browser call at all: the UI reaches it through
 * the Next.js server, never from the browser (#1771).
 */
@SpringBootTest(
    classes = [Application::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
class CorsDefaultIT : AbstractCorsIT() {

    @Test
    fun `GraphQL refuses a foreign origin by default`() {
        assertRefused(GRAPHQL, FOREIGN_ORIGIN)
        assertRefused(GRAPHQL, FOREIGN_ORIGIN, method = "GET")
        assertCallRefused(GRAPHQL, FOREIGN_ORIGIN)
    }

    @Test
    fun `REST refuses a foreign origin by default`() {
        assertRefused(REST, FOREIGN_ORIGIN, method = "GET")
        assertCallRefused(REST, FOREIGN_ORIGIN)
    }

    @Test
    fun `Extension endpoints refuse a foreign origin by default`() {
        assertRefused(EXTENSION, FOREIGN_ORIGIN, method = "GET")
        assertCallRefused(EXTENSION, FOREIGN_ORIGIN)
    }

    @Test
    fun `A client which is not a browser is still served when it sends an Origin`() {
        // A scanner, a proxy: refusing cross-origin reads is the browser's job, the API does not
        // turn the call down with a 403
        listOf(GRAPHQL, REST, EXTENSION).forEach { path ->
            val response = call(path, FOREIGN_ORIGIN)
            assertEquals(200, response.statusCode(), "$path is served: ${response.body()}")
        }
    }

    @Test
    fun `Hooks have no CORS`() {
        assertRefused(HOOK, FOREIGN_ORIGIN)
    }
}
