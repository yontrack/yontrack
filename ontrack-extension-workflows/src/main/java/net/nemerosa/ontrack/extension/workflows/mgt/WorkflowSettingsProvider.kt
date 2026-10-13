package net.nemerosa.ontrack.extension.workflows.mgt

import net.nemerosa.ontrack.model.settings.SettingsProvider
import net.nemerosa.ontrack.model.support.SettingsRepository
import net.nemerosa.ontrack.model.support.values
import net.nemerosa.ontrack.model.support.getLong
import org.springframework.stereotype.Component

@Component
class WorkflowSettingsProvider(
    private val settingsRepository: SettingsRepository,
) : SettingsProvider<WorkflowSettings> {

    override fun getSettings(): WorkflowSettings {
        val values = settingsRepository.values(WorkflowSettings::class.java)
        return WorkflowSettings(
            retentionDuration = values.getLong(
                WorkflowSettings::retentionDuration,
                WorkflowSettings.DEFAULT_RETENTION_DURATION
            ),
        )
    }

    override fun getSettingsClass(): Class<WorkflowSettings> = WorkflowSettings::class.java
}