package net.nemerosa.ontrack.extension.hook.settings

import net.nemerosa.ontrack.model.settings.SettingsProvider
import net.nemerosa.ontrack.model.support.SettingsRepository
import net.nemerosa.ontrack.model.support.values
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class HookSettingsProvider(
        private val settingsRepository: SettingsRepository,
) : SettingsProvider<HookSettings> {

    override fun getSettings(): HookSettings {
        val values = settingsRepository.values(HookSettings::class.java)
        return HookSettings(
                recordRetentionDuration = values.getString(
                        HookSettings::class.java,
                        HookSettings::recordRetentionDuration.name,
                        ""
                )
                        ?.takeIf { it.isNotBlank() }
                        ?.let { Duration.parse(it) }
                        ?: HookSettings.DEFAULT_RECORD_RETENTION_DURATION,
                recordCleanupDuration = values.getString(
                        HookSettings::class.java,
                        HookSettings::recordCleanupDuration.name,
                        ""
                )
                        ?.takeIf { it.isNotBlank() }
                        ?.let { Duration.parse(it) }
                        ?: HookSettings.DEFAULT_RECORD_CLEANUP_DURATION,
        )
    }

    override fun getSettingsClass(): Class<HookSettings> = HookSettings::class.java
}