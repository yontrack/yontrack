package net.nemerosa.ontrack.extension.bitbucket.cloud.scm

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.extension.bitbucket.cloud.bitbucketCloudTestConfigMock
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudAuthType
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudConfigurationService
import net.nemerosa.ontrack.extension.bitbucket.cloud.settings.BitbucketCloudSettings
import net.nemerosa.ontrack.extension.scm.service.SCMCloneAccess
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.fail

/**
 * [net.nemerosa.ontrack.extension.scm.service.SCM.getCloneAccess] for Bitbucket Cloud: the HTTPS
 * clone URL, without credentials, and the token under the static user name Atlassian documents for
 * each authentication type.
 */
class BitbucketCloudSCMExtensionCloneAccessTest {

    private val configurationService = mockk<BitbucketCloudConfigurationService>()

    private val extension = BitbucketCloudSCMExtension(
        extensionFeature = mockk(),
        propertyService = mockk(),
        structureService = mockk(),
        clientFactory = mockk(),
        cachedSettingsService = mockk<CachedSettingsService>().apply {
            every { getCachedSettings(BitbucketCloudSettings::class.java) } returns BitbucketCloudSettings()
        },
        configurationService = configurationService,
        issueServiceRegistry = mockk(),
        gitRepositoryClientFactory = mockk(),
        gitConfigService = mockk(),
        ontrackConfigProperties = mockk(),
    )

    private fun cloneAccess(authType: BitbucketCloudAuthType): SCMCloneAccess {
        every { configurationService.findConfiguration("config") } returns
                bitbucketCloudTestConfigMock(name = "config", authType = authType)
        val (scm, _) = extension.getSCMPath("config", "ws/repo/some/path")
            ?: fail("SCM path should have been found")
        return assertNotNull(scm.getCloneAccess(), "Bitbucket Cloud supports the clone access")
    }

    @Test
    fun `API token`() {
        val access = cloneAccess(BitbucketCloudAuthType.API_TOKEN)
        assertEquals("https://bitbucket.org/ws/repo.git", access.url)
        val credentials = assertNotNull(access.credentials)
        assertEquals("x-bitbucket-api-token-auth", credentials.username)
        assertEquals("token", credentials.password)
    }

    @Test
    fun `Access token`() {
        val access = cloneAccess(BitbucketCloudAuthType.ACCESS_TOKEN)
        assertEquals("https://bitbucket.org/ws/repo.git", access.url)
        val credentials = assertNotNull(access.credentials)
        assertEquals("x-token-auth", credentials.username)
        assertEquals("token", credentials.password)
    }

}
