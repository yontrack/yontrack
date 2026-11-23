package net.nemerosa.ontrack.extension.github.scm

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.nemerosa.ontrack.extension.github.app.GitHubAppTokenService
import net.nemerosa.ontrack.extension.github.catalog.GitHubSCMCatalogSettings
import net.nemerosa.ontrack.extension.github.model.GitHubEngineConfiguration
import net.nemerosa.ontrack.extension.github.property.GitHubConfigurator
import net.nemerosa.ontrack.extension.github.service.GitHubConfigurationService
import net.nemerosa.ontrack.extension.scm.service.SCM
import net.nemerosa.ontrack.extension.scm.service.SCMCloneAccess
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail

/**
 * [SCM.getCloneAccess] for GitHub: the HTTPS clone URL, without credentials, and the credentials of
 * each authentication type of the GitHub configuration.
 */
class GitHubSCMExtensionCloneAccessTest {

    private lateinit var gitHubConfigurationService: GitHubConfigurationService
    private lateinit var gitHubAppTokenService: GitHubAppTokenService
    private lateinit var extension: GitHubSCMExtension

    @BeforeEach
    fun init() {
        gitHubConfigurationService = mockk()
        gitHubAppTokenService = mockk()

        val cachedSettingsService = mockk<CachedSettingsService>()
        every {
            cachedSettingsService.getCachedSettings(GitHubSCMCatalogSettings::class.java)
        } returns GitHubSCMCatalogSettings()

        extension = GitHubSCMExtension(
            gitHubExtensionFeature = mockk(relaxed = true),
            propertyService = mockk(),
            clientFactory = mockk(),
            cachedSettingsService = cachedSettingsService,
            gitHubConfigurationService = gitHubConfigurationService,
            issueServiceRegistry = mockk(),
            issueServiceExtension = mockk(),
            structureService = mockk(),
            gitRepositoryClientFactory = mockk(),
            gitHubConfigurator = GitHubConfigurator(
                propertyService = mockk(),
                issueServiceRegistry = mockk(),
                issueServiceExtension = mockk(),
                ontrackGitHubClientFactory = mockk(),
                gitHubAppTokenService = gitHubAppTokenService,
            ),
            gitConfigService = mockk(),
        )
    }

    private fun scm(configuration: GitHubEngineConfiguration): SCM {
        every { gitHubConfigurationService.findConfiguration(configuration.name) } returns configuration
        val (scm, _) = extension.getSCMPath(configuration.name, "nemerosa/ontrack/some/path")
            ?: fail("SCM path should have been found")
        return scm
    }

    private fun cloneAccess(configuration: GitHubEngineConfiguration): SCMCloneAccess =
        assertNotNull(scm(configuration).getCloneAccess(), "GitHub supports the clone access")

    @Test
    fun `Anonymous access has no credentials`() {
        val access = cloneAccess(GitHubEngineConfiguration("gh", null))
        assertEquals("https://github.com/nemerosa/ontrack.git", access.url)
        assertNull(access.credentials)
    }

    @Test
    fun `The URL of a GitHub Enterprise configuration`() {
        val access = cloneAccess(
            GitHubEngineConfiguration("ghe", "https://github.example.com", oauth2Token = "tkn")
        )
        assertEquals("https://github.example.com/nemerosa/ontrack.git", access.url)
    }

    @Test
    fun `Username and password`() {
        val access = cloneAccess(
            GitHubEngineConfiguration("gh", null, user = "user", password = "secret")
        )
        assertEquals("https://github.com/nemerosa/ontrack.git", access.url)
        val credentials = assertNotNull(access.credentials)
        assertEquals("user", credentials.username)
        assertEquals("secret", credentials.password)
    }

    @Test
    fun `Username and token`() {
        val access = cloneAccess(
            GitHubEngineConfiguration("gh", null, user = "user", oauth2Token = "tkn")
        )
        val credentials = assertNotNull(access.credentials)
        assertEquals("user", credentials.username)
        assertEquals("tkn", credentials.password)
    }

    @Test
    fun `Token alone`() {
        val access = cloneAccess(
            GitHubEngineConfiguration("gh", null, oauth2Token = "tkn")
        )
        assertEquals("https://github.com/nemerosa/ontrack.git", access.url)
        val credentials = assertNotNull(access.credentials)
        assertEquals("x-access-token", credentials.username)
        assertEquals("tkn", credentials.password)
    }

    @Test
    fun `GitHub App token is fetched when the clone access is asked for`() {
        val configuration = GitHubEngineConfiguration(
            "gh",
            null,
            appId = "123",
            appPrivateKey = "key",
            appInstallationAccountName = "nemerosa",
        )
        var calls = 0
        every {
            gitHubAppTokenService.getAppInstallationToken("gh", "123", "key", "nemerosa")
        } answers { calls++; "app-token-$calls" }

        val scm = scm(configuration)
        verify(exactly = 0) { gitHubAppTokenService.getAppInstallationToken(any(), any(), any(), any()) }

        val access = assertNotNull(scm.getCloneAccess())
        assertEquals("https://github.com/nemerosa/ontrack.git", access.url)
        val credentials = assertNotNull(access.credentials)
        assertEquals("x-access-token", credentials.username)
        assertEquals("app-token-1", credentials.password)

        // Asked again, the token is read again
        assertEquals("app-token-2", scm.getCloneAccess()?.credentials?.password)
    }

}
