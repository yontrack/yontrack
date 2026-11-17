package net.nemerosa.ontrack.extension.notifications.http.auth

import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class BasicHttpAuthenticatorConfigTest {

    @Test
    fun `Parsing without a password`() {
        assertEquals(
            mapOf(
                "username" to "test",
            ).asJson().parse(),
            BasicHttpAuthenticatorConfig(
                username = "test",
                password = "",
            )
        )
    }

}