package net.nemerosa.ontrack.extension.gitlab.settings

import net.nemerosa.ontrack.extension.casc.AbstractCascTestSupport
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class GitLabSettingsCascIT : AbstractCascTestSupport() {

    @Test
    fun `Default GitLab settings`() {
        asAdmin {
            withCleanSettings<GitLabSettings> {
                val settings = cachedSettingsService.getCachedSettings(GitLabSettings::class.java)
                assertEquals(GitLabSettings.DEFAULT_MAX_COMMITS, settings.maxCommits)
                assertEquals(GitLabSettings.DEFAULT_SQUASH, settings.squash)
                assertEquals(GitLabSettings.DEFAULT_REMOVE_SOURCE_BRANCH, settings.removeSourceBranch)
                assertEquals(GitLabSettings.DEFAULT_AUTO_MERGE_TIMEOUT, settings.autoMergeTimeout)
                assertEquals(GitLabSettings.DEFAULT_AUTO_MERGE_INTERVAL, settings.autoMergeInterval)
            }
        }
    }

    @Test
    fun `GitLab settings as code`() {
        asAdmin {
            withCleanSettings<GitLabSettings> {
                casc(
                    """
                        ontrack:
                            config:
                                settings:
                                    gitlab:
                                        maxCommits: 250
                                        squash: false
                                        removeSourceBranch: false
                                        autoMergeTimeout: 60000
                                        autoMergeInterval: 5000
                    """.trimIndent()
                )
                val settings = cachedSettingsService.getCachedSettings(GitLabSettings::class.java)
                assertEquals(250, settings.maxCommits)
                assertEquals(false, settings.squash)
                assertEquals(false, settings.removeSourceBranch)
                assertEquals(60_000L, settings.autoMergeTimeout)
                assertEquals(5_000L, settings.autoMergeInterval)
            }
        }
    }

}
