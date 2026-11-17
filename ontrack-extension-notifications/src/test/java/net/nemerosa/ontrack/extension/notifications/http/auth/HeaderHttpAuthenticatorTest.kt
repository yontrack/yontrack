package net.nemerosa.ontrack.extension.notifications.http.auth

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class HeaderHttpAuthenticatorTest {

    @Test
    fun `Value is obfuscated`() {
        val authenticator = HeaderHttpAuthenticator
        val config = authenticator.obfuscate(
            HeaderHttpAuthenticatorConfig(
                name = "X-Ontrack-Token",
                value = "secret",
            )
        )
        assertEquals("X-Ontrack-Token", config.name)
        assertEquals("", config.value)
    }

    @Test
    fun `Merging a blank value`() {
        val authenticator = HeaderHttpAuthenticator
        val config = authenticator.merge(
            HeaderHttpAuthenticatorConfig(
                name = "X-Ontrack-Token",
                value = "",
            ),
            HeaderHttpAuthenticatorConfig(
                name = "X-Ontrack-Token",
                value = "xxxx",
            )
        )
        assertEquals("X-Ontrack-Token", config.name)
        assertEquals("xxxx", config.value)
    }

}