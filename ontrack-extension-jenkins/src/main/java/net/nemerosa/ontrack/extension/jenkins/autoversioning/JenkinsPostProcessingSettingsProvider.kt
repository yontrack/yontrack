package net.nemerosa.ontrack.extension.jenkins.autoversioning

import net.nemerosa.ontrack.model.settings.SettingsProvider
import net.nemerosa.ontrack.model.support.SettingsRepository
import net.nemerosa.ontrack.model.support.values
import net.nemerosa.ontrack.model.support.getInt
import net.nemerosa.ontrack.model.support.getString
import org.springframework.stereotype.Component

@Component
class JenkinsPostProcessingSettingsProvider(
    private val settingsRepository: SettingsRepository,
) : SettingsProvider<JenkinsPostProcessingSettings> {

    override fun getSettings(): JenkinsPostProcessingSettings {
        val values = settingsRepository.values(JenkinsPostProcessingSettings::class.java)
        return JenkinsPostProcessingSettings(
            config = values.getString(
                JenkinsPostProcessingSettings::config,
                ""
            ),
            job = values.getString(
                JenkinsPostProcessingSettings::job,
                ""
            ),
            retries = values.getInt(
                JenkinsPostProcessingSettings::retries,
                JenkinsPostProcessingSettings.DEFAULT_RETRIES
            ),
            retriesDelaySeconds = values.getInt(
                JenkinsPostProcessingSettings::retriesDelaySeconds,
                JenkinsPostProcessingSettings.DEFAULT_RETRIES_DELAY_SECONDS
            )
        )
    }

    override fun getSettingsClass(): Class<JenkinsPostProcessingSettings> = JenkinsPostProcessingSettings::class.java

}