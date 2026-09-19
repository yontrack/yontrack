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
                    """.trimIndent()
                )
                assertEquals(250, cachedSettingsService.getCachedSettings(GitLabSettings::class.java).maxCommits)
            }
        }
    }

}
