package net.nemerosa.ontrack.extension.github.catalog

import net.nemerosa.ontrack.extension.github.catalog.GitHubSCMCatalogSettings.Companion.DEFAULT_AUTO_MERGE_INTERVAL
import net.nemerosa.ontrack.extension.github.catalog.GitHubSCMCatalogSettings.Companion.DEFAULT_AUTO_MERGE_TIMEOUT
import net.nemerosa.ontrack.model.settings.SettingsProvider
import net.nemerosa.ontrack.model.support.SettingsRepository
import net.nemerosa.ontrack.model.support.values
import net.nemerosa.ontrack.model.support.getLong
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class GitHubSCMCatalogSettingsProvider(
    private val settingsRepository: SettingsRepository,
) : SettingsProvider<GitHubSCMCatalogSettings> {

    override fun getSettings(): GitHubSCMCatalogSettings {
        val values = settingsRepository.values(GitHubSCMCatalogSettings::class.java)
        return GitHubSCMCatalogSettings(
            orgs = values.getString(
                GitHubSCMCatalogSettings::class.java,
                GitHubSCMCatalogSettings::orgs.name,
                ""
            ).split("|"),
            autoMergeTimeout = values.getLong(
                GitHubSCMCatalogSettings::autoMergeTimeout,
                DEFAULT_AUTO_MERGE_TIMEOUT
            ),
            autoMergeInterval = values.getLong(
                GitHubSCMCatalogSettings::autoMergeInterval,
                DEFAULT_AUTO_MERGE_INTERVAL
            ),
        )
    }

    override fun getSettingsClass(): Class<GitHubSCMCatalogSettings> = GitHubSCMCatalogSettings::class.java

}