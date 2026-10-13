package net.nemerosa.ontrack.extension.sonarqube.measures

import net.nemerosa.ontrack.model.settings.SettingsProvider
import net.nemerosa.ontrack.model.support.SettingsRepository
import net.nemerosa.ontrack.model.support.values
import org.springframework.stereotype.Component

@Component
class SonarQubeMeasuresSettingsProvider(
        private val settingsRepository: SettingsRepository
) : SettingsProvider<SonarQubeMeasuresSettings> {

    override fun getSettings(): SonarQubeMeasuresSettings {
        val values = settingsRepository.values(SonarQubeMeasuresSettings::class.java)
        return SonarQubeMeasuresSettings(
                measures = values
                        .getString(SonarQubeMeasuresSettings::class.java, "measures", null)
                        ?.split("|")
                        ?: SonarQubeMeasuresSettings.DEFAULT_MEASURES,
                disabled = values
                        .getBoolean(SonarQubeMeasuresSettings::class.java, "disabled", SonarQubeMeasuresSettings.DEFAULT_DISABLED),
                coverageThreshold = values
                        .getInt(
                                SonarQubeMeasuresSettings::class.java,
                                SonarQubeMeasuresSettings::coverageThreshold.name,
                                SonarQubeMeasuresSettings.DEFAULT_COVERAGE_THRESHOLD
                        ),
                blockerThreshold = values
                        .getInt(
                                SonarQubeMeasuresSettings::class.java,
                                SonarQubeMeasuresSettings::blockerThreshold.name,
                                SonarQubeMeasuresSettings.DEFAULT_BLOCKER_THRESHOLD
                        )
        )
    }

    override fun getSettingsClass(): Class<SonarQubeMeasuresSettings> =
            SonarQubeMeasuresSettings::class.java
}