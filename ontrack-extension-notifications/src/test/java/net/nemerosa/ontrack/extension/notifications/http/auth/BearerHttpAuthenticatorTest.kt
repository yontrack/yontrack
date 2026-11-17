package net.nemerosa.ontrack.extension.notifications.http.auth

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class BearerHttpAuthenticatorTest {

    @Test
    fun `Password is obfuscated`() {
        val authenticator = BearerHttpAuthenticator
        val config = authenticator.obfuscate(
            BearerHttpAuthenticatorConfig(
                token = "secret",
            )
        )
        assertEquals("", config.token)
    }

    @Test
    fun `Merging a blank token`() {
        val authenticator = BearerHttpAuthenticator
        val config = authenticator.merge(
            BearerHttpAuthenticatorConfig(
                token = "",
            ),
            BearerHttpAuthenticatorConfig(
                token = "new-token",
            )
        )
        assertEquals("new-token", config.token)
    }

}