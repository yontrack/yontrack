package net.nemerosa.ontrack.boot.support

import net.nemerosa.ontrack.boot.Application
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

/**
 * With `ontrack.config.security.authorization.jwt.typ` set, the API accepts that type only.
 */
@SpringBootTest(
    classes = [Application::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["ontrack.config.security.authorization.jwt.typ=at+jwt"],
)
class JwtTypeCustomIT : AbstractJwtTypeIT() {

    @Test
    fun `The custom typ is accepted`() {
        assertAccepted("at+jwt")
    }

    @Test
    fun `The standard JWT typ is rejected`() {
        assertRejected("JWT")
    }

    @Test
    fun `No typ is rejected`() {
        assertRejected(null)
    }

}
