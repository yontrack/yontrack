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
                    """.trimIndent()
                )
                val settings = cachedSettingsService.getCachedSettings(BitbucketCloudSettings::class.java)
                assertEquals(250, settings.maxCommits)
            }
        }
    }

}
