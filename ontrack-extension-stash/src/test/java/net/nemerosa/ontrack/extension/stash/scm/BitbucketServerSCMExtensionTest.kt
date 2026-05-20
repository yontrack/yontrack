package net.nemerosa.ontrack.extension.stash.scm

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.extension.stash.model.StashConfiguration
import net.nemerosa.ontrack.extension.stash.service.StashConfigurationService
import net.nemerosa.ontrack.extension.stash.settings.BitbucketServerSettings
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class BitbucketServerSCMExtensionTest {

    @Test
    fun `Get SCM path from complete reference`() {

        val stashConfigurationService = mockk<StashConfigurationService>()
        every { stashConfigurationService.findConfiguration("config") } returns StashConfiguration(
            name = "config",
            url = "not used",
            user = "not used",
            password = "not used",
            autoMergeUser = "not used",
            autoMergeToken = "not used",
        )

        val cachedSettingsService = mockk<CachedSettingsService>()
        every { cachedSettingsService.getCachedSettings(BitbucketServerSettings::class.java) } returns BitbucketServerSettings()

        val extension = BitbucketServerSCMExtension(
            extensionFeature = mockk(),
            propertyService = mockk(),
            bitbucketClientFactory = mockk(),
            cachedSettingsService = cachedSettingsService,
            stashConfigurationService = stashConfigurationService,
            structureService = mockk(),
            issueServiceRegistry = mockk(),
            ontrackConfigProperties = mockk(),
            bitbucketServerConfigurator = mockk(),
            gitRepositoryClientFactory = mockk(),
            gitConfigService = mockk(),
        )
        val (scm, path) = extension.getSCMPath("config", "PROJECT/repository/some/path")
            ?: fail("SCM path should have been found")
        assertEquals("PROJECT/repository", scm.repository)
        assertEquals("some/path", path)
    }

}