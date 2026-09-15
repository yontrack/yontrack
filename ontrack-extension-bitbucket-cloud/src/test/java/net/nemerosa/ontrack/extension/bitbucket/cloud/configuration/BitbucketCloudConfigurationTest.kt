package net.nemerosa.ontrack.extension.bitbucket.cloud.configuration

import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.json.parseInto
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BitbucketCloudConfigurationTest {

    private val apiToken = BitbucketCloudConfiguration(
        name = "bbc",
        authType = BitbucketCloudAuthType.API_TOKEN,
        email = "bot@example.com",
        token = "secret",
        autoMergeEmail = "approver@example.com",
        autoMergeToken = "approver-secret",
    )

    private val accessToken = BitbucketCloudConfiguration(
        name = "bbc",
        authType = BitbucketCloudAuthType.ACCESS_TOKEN,
        email = null,
        token = "access-secret",
    )

    @Test
    fun `Obfuscation removes both tokens`() {
        val obfuscated = apiToken.obfuscate()
        assertEquals("bbc", obfuscated.name)
        assertEquals(BitbucketCloudAuthType.API_TOKEN, obfuscated.authType)
        assertEquals("bot@example.com", obfuscated.email)
        assertEquals("", obfuscated.token)
        assertEquals("approver@example.com", obfuscated.autoMergeEmail)
        assertEquals("", obfuscated.autoMergeToken)
    }

    @Test
    fun `Encryption and decryption of both tokens`() {
        val encrypted = apiToken.encrypt { it?.let { "[$it]" } }
        assertEquals("[secret]", encrypted.token)
        assertEquals("[approver-secret]", encrypted.autoMergeToken)
        assertEquals("bot@example.com", encrypted.email)
        val decrypted = encrypted.decrypt { it?.removePrefix("[")?.removeSuffix("]") }
        assertEquals(apiToken, decrypted)
    }

    @Test
    fun `No auto merge token to encrypt`() {
        val encrypted = accessToken.encrypt { it?.let { "[$it]" } }
        assertEquals("[access-secret]", encrypted.token)
        assertNull(encrypted.autoMergeToken)
    }

    @Test
    fun `Blank tokens are injected from the old configuration`() {
        val update = apiToken.obfuscate()
        assertEquals(apiToken, update.injectCredentials(apiToken))
    }

    @Test
    fun `Token is not injected when the email changes`() {
        val update = apiToken.obfuscate().copy(email = "other@example.com")
        val injected = update.injectCredentials(apiToken)
        assertEquals("", injected.token)
        assertEquals("approver-secret", injected.autoMergeToken)
    }

    @Test
    fun `Token is not injected when the authentication type changes`() {
        val update = BitbucketCloudConfiguration(
            name = "bbc",
            authType = BitbucketCloudAuthType.ACCESS_TOKEN,
            email = null,
            token = "",
        )
        assertEquals("", update.injectCredentials(apiToken).token)
    }

    @Test
    fun `Auto merge token is not injected when the auto merge email changes`() {
        val update = apiToken.obfuscate().copy(autoMergeEmail = "other@example.com")
        val injected = update.injectCredentials(apiToken)
        assertEquals("secret", injected.token)
        assertEquals("", injected.autoMergeToken)
    }

    @Test
    fun `A provided token is kept`() {
        val update = apiToken.copy(token = "new-secret", autoMergeToken = "new-approver-secret")
        assertEquals(update, update.injectCredentials(apiToken))
    }

    @Test
    fun `API token needs an email and a token`() {
        apiToken.checkFields()
        assertThrows<BitbucketCloudConfigurationMissingFieldException> {
            apiToken.copy(email = " ").checkFields()
        }
        assertThrows<BitbucketCloudConfigurationMissingFieldException> {
            apiToken.copy(token = null).checkFields()
        }
    }

    @Test
    fun `Access token needs a token only`() {
        accessToken.checkFields()
        assertThrows<BitbucketCloudConfigurationMissingFieldException> {
            accessToken.copy(token = "").checkFields()
        }
    }

    @Test
    fun `Auto merge token needs an auto merge email`() {
        assertThrows<BitbucketCloudConfigurationMissingFieldException> {
            apiToken.copy(autoMergeEmail = null).checkFields()
        }
    }

    @Test
    fun `JSON round trip`() {
        val json = apiToken.asJson()
        assertEquals(apiToken, json.parseInto(BitbucketCloudConfiguration::class))
    }

    @Test
    fun `Parsing JSON with default values`() {
        val config = """{"name":"bbc","authType":"ACCESS_TOKEN","token":"t"}"""
            .parseAsJson().parseInto(BitbucketCloudConfiguration::class)
        assertEquals(accessToken.copy(token = "t"), config)
    }
}
