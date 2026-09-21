package net.nemerosa.ontrack.boot.support

import net.nemerosa.ontrack.boot.Application
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

/**
 * With no `ontrack.config.security.authorization.jwt.typ`, the API accepts the standard `JWT` type,
 * or no type at all, and nothing else.
 */
@SpringBootTest(
    classes = [Application::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
class JwtTypeDefaultIT : AbstractJwtTypeIT() {

    @Test
    fun `A JWT typ is accepted`() {
        assertAccepted("JWT")
    }

    @Test
    fun `No typ is accepted`() {
        assertAccepted(null)
    }

    @Test
    fun `Another typ is rejected`() {
        assertRejected("at+jwt")
    }

}
