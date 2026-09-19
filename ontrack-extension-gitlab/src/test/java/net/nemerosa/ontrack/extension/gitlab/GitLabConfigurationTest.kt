package net.nemerosa.ontrack.extension.gitlab

import com.fasterxml.jackson.core.JsonProcessingException
import net.nemerosa.ontrack.extension.gitlab.model.GitLabConfiguration
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.test.TestUtils
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class GitLabConfigurationTest {

    @Test
    fun toJson() {
        TestUtils.assertJsonWrite(
            mapOf(
                "name" to "ontrack",
                "url" to "https://gitlab.nemerosa.net",
                "token" to "1234567890abcdef",
                "ignoreSslCertificate" to false,
            ).asJson(),
            configurationFixture()
        )
    }

    @Test
    @Throws(JsonProcessingException::class)
    fun fromJson() {
        TestUtils.assertJsonRead<GitLabConfiguration?>(
            configurationFixture(),
            mapOf(
                "name" to "ontrack",
                "url" to "https://gitlab.nemerosa.net",
                "token" to "1234567890abcdef",
                "ignoreSslCertificate" to false,
            ).asJson(),
            GitLabConfiguration::class.java
        )
    }

    @Test
    fun `A configuration stored before the token rework can still be read`() {
        val configuration = mapOf(
            "name" to "ontrack",
            "url" to "https://gitlab.nemerosa.net",
            "user" to "some-user",
            "password" to "1234567890abcdef",
            "ignoreSslCertificate" to true,
        ).asJson().parse<GitLabConfiguration>()
        assertEquals("ontrack", configuration.name)
        assertEquals("https://gitlab.nemerosa.net", configuration.url)
        assertEquals(true, configuration.ignoreSslCertificate)
    }

    @Test
    fun obfuscate() {
        assertEquals("", configurationFixture().obfuscate().token)
    }

    @Test
    fun `Encryption and decryption of the token`() {
        val encrypted = configurationFixture().encrypt { it?.let { plain -> "enc-$plain" } }
        assertEquals("enc-1234567890abcdef", encrypted.token)
        assertEquals("1234567890abcdef", encrypted.decrypt { it?.removePrefix("enc-") }.token)
    }

    @Test
    fun `A blank token keeps the old one`() {
        val old = configurationFixture()
        assertEquals("1234567890abcdef", old.copy(token = "").injectCredentials(old).token)
    }

    @Test
    fun `A new token replaces the old one`() {
        val old = configurationFixture()
        assertEquals("new", old.copy(token = "new").injectCredentials(old).token)
    }

    @Test
    fun `The token never shows up in the string representation`() {
        assertEquals(
            "GitLabConfiguration(name=ontrack, url=https://gitlab.nemerosa.net)",
            configurationFixture().toString()
        )
    }

    private fun configurationFixture() = GitLabConfiguration(
        name = "ontrack",
        url = "https://gitlab.nemerosa.net",
        token = "1234567890abcdef",
        ignoreSslCertificate = false,
    )
}
