package net.nemerosa.ontrack.extension.bitbucket.cloud

import com.fasterxml.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.support.client.jackson2RestTemplateBuilder
import org.springframework.web.client.RestTemplate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Checks the test workspace itself: the secrets behind the real tests, and the fixture repository.
 */
class BitbucketCloudTestWorkspaceIT {

    private val env: BitbucketCloudTestEnv get() = bitbucketCloudTestEnv

    private fun basic(identity: BitbucketCloudTestIdentity): RestTemplate =
        jackson2RestTemplateBuilder()
            .rootUri(BitbucketCloudTestRestApi.ROOT)
            .basicAuthentication(identity.email, identity.token)
            .build()

    private fun bearer(token: String): RestTemplate =
        jackson2RestTemplateBuilder()
            .rootUri(BitbucketCloudTestRestApi.ROOT)
            .defaultHeader("Authorization", "Bearer $token")
            .build()

    private fun assertReadsRepository(template: RestTemplate) {
        val repository = template.getForObject(
            "/2.0/repositories/{workspace}/{repository}",
            JsonNode::class.java,
            env.workspace,
            env.repository,
        )
        assertNotNull(repository, "Repository is readable") {
            assertEquals(env.project, it.path("project").path("key").asText(), "Repository project")
        }
    }

    @TestOnBitbucketCloud
    fun `The tokens are not about to expire`() {
        val days = env.daysBeforeTokensExpire()
        assertTrue(
            days >= 14,
            "The Bitbucket Cloud test tokens expire on ${env.tokensExpiry} ($days day(s) left). " +
                    "Renew them with ontrack-extension-bitbucket-cloud/scripts/bitbucket-cloud-test-workspace.sh."
        )
    }

    @TestOnBitbucketCloud
    fun `The bot reads the fixture repository`() {
        assertReadsRepository(basic(env.bot))
    }

    @TestOnBitbucketCloud
    fun `The approver reads the fixture repository`() {
        assertReadsRepository(basic(env.approver))
    }

    @TestOnBitbucketCloud
    fun `The access token reads the fixture repository`() {
        assertReadsRepository(bearer(env.accessToken))
    }

    @TestOnBitbucketCloud
    fun `The fixture repository holds the fixture pipelines`() {
        val expected = BitbucketCloudTestFixture::class.java
            .getResourceAsStream("${BitbucketCloudTestFixture.RESOURCE_DIR}/${BitbucketCloudTestFixture.PIPELINES_FILE}")
            ?.use { it.reader().readText() }
        val actual = basic(env.bot).getForObject(
            "/2.0/repositories/{workspace}/{repository}/src/{branch}/{path}",
            String::class.java,
            env.workspace,
            env.repository,
            BitbucketCloudTestFixture.MAIN_BRANCH,
            BitbucketCloudTestFixture.PIPELINES_FILE,
        )
        assertEquals(
            expected?.trimEnd(),
            actual?.trimEnd(),
            "The fixture pipelines are out of date: re-run the fixture stage of the wizard."
        )
    }

}
