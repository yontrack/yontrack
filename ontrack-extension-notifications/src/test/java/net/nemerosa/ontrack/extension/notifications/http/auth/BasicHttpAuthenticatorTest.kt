package net.nemerosa.ontrack.extension.notifications.http.auth

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class BasicHttpAuthenticatorTest {

    @Test
    fun `Password is obfuscated`() {
        val authenticator = BasicHttpAuthenticator
        val config = authenticator.obfuscate(
            BasicHttpAuthenticatorConfig(
                username = "user",
                password = "password",
            )
        )
        assertEquals("user", config.username)
        assertEquals("", config.password)
    }

    @Test
    fun `Merging a blank password`() {
        val authenticator = BasicHttpAuthenticator
        val config = authenticator.merge(
            BasicHttpAuthenticatorConfig(
                username = "new-user",
                password = "",
            ),
            BasicHttpAuthenticatorConfig(
                username = "user",
                password = "xxxx",
            )
        )
        assertEquals("new-user", config.username)
        assertEquals("xxxx", config.password)
    }

}