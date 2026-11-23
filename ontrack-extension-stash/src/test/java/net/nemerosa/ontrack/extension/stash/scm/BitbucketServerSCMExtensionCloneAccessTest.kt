package net.nemerosa.ontrack.extension.stash.scm

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.extension.scm.service.SCMCloneAccess
import net.nemerosa.ontrack.extension.stash.model.StashConfiguration
import net.nemerosa.ontrack.extension.stash.service.StashConfigurationService
import net.nemerosa.ontrack.extension.stash.settings.BitbucketServerSettings
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail

/**
 * [net.nemerosa.ontrack.extension.scm.service.SCM.getCloneAccess] for Bitbucket Server: the HTTPS
 * clone URL, without credentials, and the user and password (or token) of the configuration.
 */
class BitbucketServerSCMExtensionCloneAccessTest {

    private val stashConfigurationService = mockk<StashConfigurationService>()

    private val extension = BitbucketServerSCMExtension(
        extensionFeature = mockk(),
        propertyService = mockk(),
        bitbucketClientFactory = mockk(),
        cachedSettingsService = mockk<CachedSettingsService>().apply {
            every { getCachedSettings(BitbucketServerSettings::class.java) } returns BitbucketServerSettings()
        },
        stashConfigurationService = stashConfigurationService,
        structureService = mockk(),
        issueServiceRegistry = mockk(),
        ontrackConfigProperties = mockk(),
        bitbucketServerConfigurator = mockk(),
        gitRepositoryClientFactory = mockk(),
        gitConfigService = mockk(),
    )

    private fun cloneAccess(user: String?, password: String?): SCMCloneAccess {
        every { stashConfigurationService.findConfiguration("config") } returns StashConfiguration(
            name = "config",
            url = "https://bitbucket.example.com",
            user = user,
            password = password,
            autoMergeUser = null,
            autoMergeToken = null,
        )
        val (scm, _) = extension.getSCMPath("config", "PROJECT/repository/some/path")
            ?: fail("SCM path should have been found")
        return assertNotNull(scm.getCloneAccess(), "Bitbucket Server supports the clone access")
    }

    @Test
    fun `User and password`() {
        val access = cloneAccess("user", "secret")
        assertEquals("https://bitbucket.example.com/scm/PROJECT/repository.git", access.url)
        val credentials = assertNotNull(access.credentials)
        assertEquals("user", credentials.username)
        assertEquals("secret", credentials.password)
    }

    @Test
    fun `No user means an anonymous access`() {
        val access = cloneAccess(null, null)
        assertEquals("https://bitbucket.example.com/scm/PROJECT/repository.git", access.url)
        assertNull(access.credentials)
    }

}
