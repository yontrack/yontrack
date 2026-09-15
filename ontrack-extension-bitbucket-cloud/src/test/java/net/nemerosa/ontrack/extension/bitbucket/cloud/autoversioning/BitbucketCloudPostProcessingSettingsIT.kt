package net.nemerosa.ontrack.extension.bitbucket.cloud.autoversioning

import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class BitbucketCloudPostProcessingSettingsIT : AbstractDSLTestSupport() {

    @Test
    fun `Default settings`() {
        withCleanSettings<BitbucketCloudPostProcessingSettings> {
            val settings = cachedSettingsService.getCachedSettings(BitbucketCloudPostProcessingSettings::class.java)
            assertEquals("", settings.config)
            assertEquals("", settings.workspace)
            assertEquals("", settings.repository)
            assertEquals("", settings.pipeline)
            assertEquals("main", settings.branch)
            assertEquals(10, settings.retries)
            assertEquals(30, settings.retriesDelaySeconds)
        }
    }

    @Test
    fun `Saving empty settings`() {
        withCleanSettings<BitbucketCloudPostProcessingSettings> {
            asAdmin {
                settingsManagerService.saveSettings(
                    BitbucketCloudPostProcessingSettings(
                        config = null,
                        workspace = null,
                        repository = null,
                        pipeline = null,
                        branch = "main",
                        retries = 30,
                        retriesDelaySeconds = 10,
                    )
                )
            }
            val saved = cachedSettingsService.getCachedSettings(BitbucketCloudPostProcessingSettings::class.java)
            assertEquals("", saved.config)
            assertEquals("", saved.workspace)
            assertEquals("", saved.repository)
            assertEquals("", saved.pipeline)
            assertEquals("main", saved.branch)
            assertEquals(30, saved.retries)
            assertEquals(10, saved.retriesDelaySeconds)
        }
    }

    @Test
    fun `Saving complete settings`() {
        withCleanSettings<BitbucketCloudPostProcessingSettings> {
            asAdmin {
                settingsManagerService.saveSettings(
                    BitbucketCloudPostProcessingSettings(
                        config = "my-config",
                        workspace = "my-ws",
                        repository = "my-repo",
                        pipeline = "yontrack-auto-versioning",
                        branch = "develop",
                        retries = 20,
                        retriesDelaySeconds = 15,
                    )
                )
            }
            val saved = cachedSettingsService.getCachedSettings(BitbucketCloudPostProcessingSettings::class.java)
            assertEquals("my-config", saved.config)
            assertEquals("my-ws", saved.workspace)
            assertEquals("my-repo", saved.repository)
            assertEquals("yontrack-auto-versioning", saved.pipeline)
            assertEquals("develop", saved.branch)
            assertEquals(20, saved.retries)
            assertEquals(15, saved.retriesDelaySeconds)
        }
    }

}
