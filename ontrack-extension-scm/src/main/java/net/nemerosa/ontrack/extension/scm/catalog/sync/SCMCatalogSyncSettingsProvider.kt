package net.nemerosa.ontrack.extension.scm.catalog.sync

import net.nemerosa.ontrack.model.settings.SettingsProvider
import net.nemerosa.ontrack.model.support.SettingsRepository
import net.nemerosa.ontrack.model.support.values
import net.nemerosa.ontrack.model.support.getBoolean
import net.nemerosa.ontrack.model.support.getString
import org.springframework.stereotype.Component

/**
 * Reading the SCM catalog sync settings.
 */
@Component
class SCMCatalogSyncSettingsProvider(
    private val settingsRepository: SettingsRepository,
) : SettingsProvider<SCMCatalogSyncSettings> {

    override fun getSettings(): SCMCatalogSyncSettings {
        val values = settingsRepository.values(SCMCatalogSyncSettings::class.java)
        return SCMCatalogSyncSettings(
            syncEnabled = values.getBoolean(
                SCMCatalogSyncSettings::syncEnabled,
                DEFAULT_SCM_CATALOG_SYNC_SETTINGS_ENABLED
            ),
            orphanDisablingEnabled = values.getBoolean(
                SCMCatalogSyncSettings::orphanDisablingEnabled,
                DEFAULT_SCM_CATALOG_SYNC_SETTINGS_ORPHAN_DISABLED
            ),
            scm = values.getString(
                SCMCatalogSyncSettings::scm,
                DEFAULT_SCM_CATALOG_SYNC_SETTINGS_SCM
            ),
            config = values.getString(
                SCMCatalogSyncSettings::config,
                DEFAULT_SCM_CATALOG_SYNC_SETTINGS_CONFIG
            ),
            repository = values.getString(
                SCMCatalogSyncSettings::repository,
                DEFAULT_SCM_CATALOG_SYNC_SETTINGS_REPOSITORY
            ),
        )
    }

    override fun getSettingsClass(): Class<SCMCatalogSyncSettings> = SCMCatalogSyncSettings::class.java

}