package net.nemerosa.ontrack.extension.notifications.http.config

import net.nemerosa.ontrack.extension.notifications.http.auth.HttpAuthentication
import net.nemerosa.ontrack.extension.notifications.http.auth.HttpAuthenticatorNotFoundException
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class HttpConfigurationTest {

    private fun config(
        url: String = "https://api.example.com/v1",
        authentication: HttpAuthentication = basic("secret"),
    ) = HttpConfiguration(
        name = "api",
        url = url,
        authentication = authentication,
    )

    private fun basic(password: String) = HttpAuthentication(
        type = "basic",
        config = mapOf("username" to "user", "password" to password).asJson(),
    )

    @ParameterizedTest
    @ValueSource(
        strings = [
            "https://api.example.com",
            "https://api.example.com/",
            "http://localhost:8080/api/v1",
            "HTTPS://api.example.com/v1/",
        ]
    )
    fun `Valid base URLs`(url: String) {
        config(url = url).check()
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "",
            "api.example.com",
            "/api/v1",
            "ftp://api.example.com",
            "file:///etc/passwd",
            "https://",
            "https://user:password@api.example.com",
            "https://api.example.com/v1?token=secret",
            "https://api.example.com/v1#fragment",
            "https://api.example.com/v 1",
        ]
    )
    fun `Invalid base URLs`(url: String) {
        assertThrows<HttpConfigurationUrlException> {
            config(url = url).check()
        }
    }

    @Test
    fun `Unknown authentication type`() {
        assertThrows<HttpAuthenticatorNotFoundException> {
            config(authentication = HttpAuthentication(type = "oauth2")).check()
        }
    }

    @Test
    fun `No authentication by default`() {
        val config = mapOf(
            "name" to "api",
            "url" to "https://api.example.com",
        ).asJson().parse<HttpConfiguration>()
        assertEquals(HttpAuthentication.none(), config.authentication)
        config.check()
    }

    @Test
    fun `Obfuscation drops the secret`() {
        assertEquals(
            config(authentication = basic("")),
            config().obfuscate(),
        )
    }

    @Test
    fun `Encryption round trip`() {
        val original = config()
        val encrypted = original.encrypt { plain -> plain?.let { "encrypted:${it.reversed()}" } }
        assertNotEquals(original, encrypted)
        assertTrue(encrypted.authentication.config.isString, "The whole authentication configuration is encrypted")
        assertTrue(!encrypted.authentication.config.asString().contains("secret"), "The secret is not in clear")
        assertEquals("basic", encrypted.authentication.type)
        assertEquals(original.url, encrypted.url)
        val decrypted = encrypted.decrypt { it?.removePrefix("encrypted:")?.reversed() }
        assertEquals(original, decrypted)
    }

    @Test
    fun `Injecting the existing credentials on a blank secret`() {
        assertEquals(
            config(authentication = basic("existing")),
            config(authentication = basic("")).injectCredentials(config(authentication = basic("existing"))),
        )
    }

    @Test
    fun `Not injecting the existing credentials on a new secret`() {
        assertEquals(
            config(authentication = basic("new")),
            config(authentication = basic("new")).injectCredentials(config(authentication = basic("existing"))),
        )
    }

    @Test
    fun `Not injecting the existing credentials on another type`() {
        val input = config(
            authentication = HttpAuthentication(
                type = "bearer",
                config = mapOf("token" to "").asJson(),
            )
        )
        assertEquals(
            input,
            input.injectCredentials(config(authentication = basic("existing"))),
        )
    }

}
