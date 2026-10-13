package net.nemerosa.ontrack.service.job

import net.nemerosa.ontrack.common.parseDuration
import net.nemerosa.ontrack.model.settings.JobHistorySettings
import net.nemerosa.ontrack.model.settings.SettingsProvider
import net.nemerosa.ontrack.model.support.SettingsRepository
import net.nemerosa.ontrack.model.support.values
import org.springframework.stereotype.Component

@Component
class JobHistorySettingsProvider(
    private val settingsRepository: SettingsRepository,
) : SettingsProvider<JobHistorySettings> {

    override fun getSettings(): JobHistorySettings {
        val values = settingsRepository.values(JobHistorySettings::class.java)
        return JobHistorySettings(
            retention = values.getString(
                JobHistorySettings::class.java,
                JobHistorySettings::retention.name,
                ""
            )
                ?.takeIf { it.isNotBlank() }
                ?.let { parseDuration(it) }
                ?: JobHistorySettings.DEFAULT_JOB_HISTORY_RETENTION,
        )
    }

    override fun getSettingsClass(): Class<JobHistorySettings> = JobHistorySettings::class.java
}