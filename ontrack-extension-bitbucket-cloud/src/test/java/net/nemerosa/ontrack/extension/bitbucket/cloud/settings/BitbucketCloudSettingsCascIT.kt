package net.nemerosa.ontrack.extension.bitbucket.cloud.settings

import net.nemerosa.ontrack.extension.casc.AbstractCascTestSupport
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class BitbucketCloudSettingsCascIT : AbstractCascTestSupport() {

    @Test
    fun `Default Bitbucket Cloud settings`() {
        asAdmin {
            withCleanSettings<BitbucketCloudSettings> {
                val settings = cachedSettingsService.getCachedSettings(BitbucketCloudSettings::class.java)
                assertEquals(BitbucketCloudSettings.DEFAULT_MAX_COMMITS, settings.maxCommits)
                assertEquals(BitbucketCloudMergeStrategy.squash, settings.mergeStrategy)
                assertEquals(600_000L, settings.autoMergeTimeout)
                assertEquals(30_000L, settings.autoMergeInterval)
                assertEquals(true, settings.autoDeleteBranch)
            }
        }
    }

    @Test
    fun `Bitbucket Cloud settings as code`() {
        asAdmin {
            withCleanSettings<BitbucketCloudSettings> {
                casc(
                    """
                        ontrack:
                            config:
                                settings:
                                    bitbucket-cloud:
                                        maxCommits: 250
                                        mergeStrategy: fast_forward
                                        autoMergeTimeout: 120000
                                        autoMergeInterval: 5000
                                        autoDeleteBranch: false
                    """.trimIndent()
                )
                val settings = cachedSettingsService.getCachedSettings(BitbucketCloudSettings::class.java)
                assertEquals(250, settings.maxCommits)
                assertEquals(BitbucketCloudMergeStrategy.fast_forward, settings.mergeStrategy)
                assertEquals(120_000L, settings.autoMergeTimeout)
                assertEquals(5_000L, settings.autoMergeInterval)
                assertEquals(false, settings.autoDeleteBranch)
            }
        }
    }

}
