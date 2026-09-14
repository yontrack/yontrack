package net.nemerosa.ontrack.extension.bitbucket.cloud

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BitbucketCloudTestEnvTest {

    private val complete = mapOf(
        BitbucketCloudTestProperties.WORKSPACE to "yontrack-test",
        BitbucketCloudTestProperties.PROJECT to "YONTRACK",
        BitbucketCloudTestProperties.REPOSITORY to "yontrack-fixture",
        BitbucketCloudTestProperties.BOT_EMAIL to "bot@example.com",
        BitbucketCloudTestProperties.BOT_TOKEN to "bot-token",
        BitbucketCloudTestProperties.APPROVER_EMAIL to "approver@example.com",
        BitbucketCloudTestProperties.APPROVER_TOKEN to "approver-token",
        BitbucketCloudTestProperties.ACCESS_TOKEN to "access-token",
        BitbucketCloudTestProperties.TOKENS_EXPIRY to "2027-09-01",
    )

    @Test
    fun `Real tests are skipped without any credentials`() {
        assertFalse(bitbucketCloudTestEnabled { null })
    }

    @Test
    fun `Real tests are skipped when explicitly ignored, even with credentials`() {
        val values = complete + (BitbucketCloudTestProperties.IGNORE to "true")
        assertFalse(bitbucketCloudTestEnabled(values::get))
    }

    @Test
    fun `Real tests are enabled with all the credentials`() {
        assertTrue(bitbucketCloudTestEnabled(complete::get))
    }

    @Test
    fun `A partial set of credentials is a misconfiguration, not a skip`() {
        val values = complete - BitbucketCloudTestProperties.APPROVER_TOKEN
        val ex = assertThrows<IllegalStateException> {
            bitbucketCloudTestEnabled(values::get)
        }
        assertTrue(BitbucketCloudTestProperties.APPROVER_TOKEN in (ex.message ?: ""), ex.message)
    }

    @Test
    fun `Pipeline tests need their own switch on top of the credentials`() {
        assertFalse(bitbucketCloudPipelinesTestEnabled(complete::get))
        val values = complete + (BitbucketCloudTestProperties.PIPELINES to "true")
        assertTrue(bitbucketCloudPipelinesTestEnabled(values::get))
    }

    @Test
    fun `Pipeline tests are skipped without credentials even when switched on`() {
        val values = mapOf(BitbucketCloudTestProperties.PIPELINES to "true")
        assertFalse(bitbucketCloudPipelinesTestEnabled(values::get))
    }

    @Test
    fun `Reading the environment`() {
        val env = readBitbucketCloudTestEnv(complete::get)
        assertEquals("yontrack-test", env.workspace)
        assertEquals("YONTRACK", env.project)
        assertEquals("yontrack-fixture", env.repository)
        assertEquals("bot@example.com", env.bot.email)
        assertEquals("bot-token", env.bot.token)
        assertEquals("approver@example.com", env.approver.email)
        assertEquals("approver-token", env.approver.token)
        assertEquals("access-token", env.accessToken)
        assertEquals(LocalDate.of(2027, 9, 1), env.tokensExpiry)
    }

    @Test
    fun `Days left before the tokens expire`() {
        val env = readBitbucketCloudTestEnv(complete::get)
        assertEquals(31, env.daysBeforeTokensExpire(LocalDate.of(2027, 8, 1)))
        assertEquals(-1, env.daysBeforeTokensExpire(LocalDate.of(2027, 9, 2)))
    }

    @Test
    fun `System property names map to the CI environment variable names`() {
        assertEquals(
            "ONTRACK_TEST_EXTENSION_BITBUCKET_CLOUD_APPROVER_TOKEN",
            BitbucketCloudTestProperties.envName(BitbucketCloudTestProperties.APPROVER_TOKEN)
        )
    }

}
