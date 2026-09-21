package net.nemerosa.ontrack.extension.github.app

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.common.toJavaDate
import net.nemerosa.ontrack.extension.github.app.client.GitHubAppAccount
import net.nemerosa.ontrack.extension.github.app.client.GitHubAppClient
import net.nemerosa.ontrack.extension.github.app.client.GitHubAppInstallation
import net.nemerosa.ontrack.extension.github.app.client.GitHubAppInstallationToken
import net.nemerosa.ontrack.model.support.OntrackConfigProperties
import net.nemerosa.ontrack.test.TestUtils
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultGitHubAppTokenServiceTest {

    private lateinit var client: GitHubAppClient
    private lateinit var service: GitHubAppTokenService

    @BeforeEach
    fun before() {
        client = mockk()
        service = DefaultGitHubAppTokenService(client, OntrackConfigProperties())
    }

    @Test
    fun `Getting a new token when there is none yet`() {
        configureClient()

        val token = service.getAppInstallationToken(
            configurationName = TEST_CONFIG,
            appId = TEST_APP_ID,
            appPrivateKey = TestUtils.resourceString(TEST_APP_PRIVATE_KEY_RESOURCE_PATH),
            appInstallationAccountName = TEST_APP_INSTALLATION_ACCOUNT_LOGIN
        )
        assertTrue(token != null && token.isNotBlank(), "Token has been generated")

        // Gets the token again, it must be the same
        val secondToken = service.getAppInstallationToken(
            configurationName = TEST_CONFIG,
            appId = TEST_APP_ID,
            appPrivateKey = TestUtils.resourceString(TEST_APP_PRIVATE_KEY_RESOURCE_PATH),
            appInstallationAccountName = TEST_APP_INSTALLATION_ACCOUNT_LOGIN
        )
        assertEquals(secondToken, token, "Reusing the same token")
    }

    @Test
    fun `Getting a new token after it's been invalidated`() {
        configureClient()

        val token = service.getAppInstallationToken(
            configurationName = TEST_CONFIG,
            appId = TEST_APP_ID,
            appPrivateKey = TestUtils.resourceString(TEST_APP_PRIVATE_KEY_RESOURCE_PATH),
            appInstallationAccountName = TEST_APP_INSTALLATION_ACCOUNT_LOGIN
        )
        assertTrue(token != null && token.isNotBlank(), "Token has been generated")
        
        // Invalidates the token
        service.invalidateAppInstallationToken(TEST_CONFIG)

        // Gets the token again, it must be another one
        val secondToken = service.getAppInstallationToken(
            configurationName = TEST_CONFIG,
            appId = TEST_APP_ID,
            appPrivateKey = TestUtils.resourceString(TEST_APP_PRIVATE_KEY_RESOURCE_PATH),
            appInstallationAccountName = TEST_APP_INSTALLATION_ACCOUNT_LOGIN
        )
        assertTrue(secondToken != token, "A new token has been generated")
    }

    private fun configureClient() {
        every { client.getAppInstallations(any()) } returns listOf(
            GitHubAppInstallation(
                TEST_APP_INSTALLATION_ID,
                GitHubAppAccount(TEST_APP_INSTALLATION_ACCOUNT_LOGIN, "")
            )
        )
        every {
            client.generateInstallationToken(
                any(),
                TEST_APP_INSTALLATION_ID
            )
        } returns uniqueTestInstallationToken() andThen uniqueTestInstallationToken()
    }

    private fun uniqueTestInstallationToken() = GitHubAppInstallationToken(
        uid("T"),
        Time.now().plusHours(1).toJavaDate()
    )

    companion object {
        const val TEST_CONFIG = "test"
        const val TEST_APP_ID = "123456"
        const val TEST_APP_PRIVATE_KEY_RESOURCE_PATH = "/test-app.pem"
        const val TEST_APP_INSTALLATION_ID = "1234567890"
        const val TEST_APP_INSTALLATION_ACCOUNT_LOGIN = "test"
    }
}