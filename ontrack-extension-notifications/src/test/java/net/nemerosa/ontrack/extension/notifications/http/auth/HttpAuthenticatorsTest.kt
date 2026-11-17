package net.nemerosa.ontrack.extension.notifications.http.auth

import net.nemerosa.ontrack.json.asJson
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HttpAuthenticatorsTest {

    @Test
    fun `The four authentication types, in order`() {
        assertEquals(
            listOf("basic", "bearer", "header", "none"),
            HttpAuthenticators.authenticators.map { it.type },
        )
    }

    @Test
    fun `Unknown authentication type`() {
        assertNull(HttpAuthenticators.findAuthenticator("oauth2"))
        assertThrows<HttpAuthenticatorNotFoundException> {
            HttpAuthenticators.getAuthenticator("oauth2")
        }
    }

    @Test
    fun `Headers of the basic authentication`() {
        assertEquals(
            mapOf("Authorization" to "Basic dXNlcjpzZWNyZXQ="),
            HttpAuthentication(
                type = "basic",
                config = mapOf("username" to "user", "password" to "secret").asJson(),
            ).headers(),
        )
    }

    @Test
    fun `Headers of the bearer authentication`() {
        assertEquals(
            mapOf("Authorization" to "Bearer secret"),
            HttpAuthentication(
                type = "bearer",
                config = mapOf("token" to "secret").asJson(),
            ).headers(),
        )
    }

    @Test
    fun `Headers of the header authentication`() {
        assertEquals(
            mapOf("X-Api-Key" to "secret"),
            HttpAuthentication(
                type = "header",
                config = mapOf("name" to "X-Api-Key", "value" to "secret").asJson(),
            ).headers(),
        )
    }

    @Test
    fun `No header for the none authentication`() {
        assertEquals(
            emptyMap(),
            HttpAuthentication.none().headers(),
        )
    }

    @Test
    fun `The none authentication ignores any configuration`() {
        val auth = HttpAuthentication(type = "none", config = mapOf("any" to "thing").asJson())
        assertEquals(emptyMap(), auth.headers())
        assertEquals(emptyMap<String, String>().asJson(), auth.obfuscate().config)
    }

    @Test
    fun `Obfuscating an authentication`() {
        assertEquals(
            HttpAuthentication(
                type = "basic",
                config = mapOf("username" to "user", "password" to "").asJson(),
            ),
            HttpAuthentication(
                type = "basic",
                config = mapOf("username" to "user", "password" to "secret").asJson(),
            ).obfuscate(),
        )
    }

    @Test
    fun `Obfuscating an unreadable authentication drops its configuration`() {
        assertEquals(
            HttpAuthentication(type = "basic", config = emptyMap<String, String>().asJson()),
            HttpAuthentication(type = "basic", config = "encrypted-text".asJson()).obfuscate(),
        )
        assertEquals(
            HttpAuthentication(type = "unknown", config = emptyMap<String, String>().asJson()),
            HttpAuthentication(type = "unknown", config = mapOf("token" to "x").asJson()).obfuscate(),
        )
    }

    @Test
    fun `Merging keeps the existing secret on a blank input`() {
        assertEquals(
            HttpAuthentication(
                type = "bearer",
                config = mapOf("token" to "existing").asJson(),
            ),
            HttpAuthentication(
                type = "bearer",
                config = mapOf("token" to "").asJson(),
            ).merge(
                HttpAuthentication(
                    type = "bearer",
                    config = mapOf("token" to "existing").asJson(),
                )
            ),
        )
    }

    @Test
    fun `Merging replaces the secret on a non blank input`() {
        assertEquals(
            HttpAuthentication(
                type = "header",
                config = mapOf("name" to "X-Key", "value" to "new").asJson(),
            ),
            HttpAuthentication(
                type = "header",
                config = mapOf("name" to "X-Key", "value" to "new").asJson(),
            ).merge(
                HttpAuthentication(
                    type = "header",
                    config = mapOf("name" to "X-Old", "value" to "existing").asJson(),
                )
            ),
        )
    }

    @Test
    fun `Merging with another type takes the input as it is`() {
        val input = HttpAuthentication(
            type = "bearer",
            config = mapOf("token" to "").asJson(),
        )
        assertEquals(
            input,
            input.merge(
                HttpAuthentication(
                    type = "basic",
                    config = mapOf("username" to "user", "password" to "existing").asJson(),
                )
            ),
        )
    }

    @Test
    fun `Validating an authentication`() {
        HttpAuthentication(
            type = "basic",
            config = mapOf("username" to "user").asJson(),
        ).validate()
        HttpAuthentication.none().validate()
    }

    @Test
    fun `Validating an authentication with an unknown type`() {
        assertThrows<HttpAuthenticatorNotFoundException> {
            HttpAuthentication(type = "oauth2", config = emptyMap<String, String>().asJson()).validate()
        }
    }

    @Test
    fun `Validating an authentication with an invalid configuration`() {
        assertThrows<HttpAuthenticationConfigException> {
            HttpAuthentication(type = "basic", config = mapOf("password" to "x").asJson()).validate()
        }
    }

}
