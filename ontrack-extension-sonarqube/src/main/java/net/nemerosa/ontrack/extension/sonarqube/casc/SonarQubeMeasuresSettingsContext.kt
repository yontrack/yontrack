package net.nemerosa.ontrack.extension.sonarqube.casc

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.casc.context.settings.AbstractSubSettingsContext
import net.nemerosa.ontrack.extension.sonarqube.measures.SonarQubeMeasuresSettings
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import net.nemerosa.ontrack.model.settings.SettingsManagerService
import org.springframework.stereotype.Component

@Component
class SonarQubeMeasuresSettingsContext(
    settingsManagerService: SettingsManagerService,
    cachedSettingsService: CachedSettingsService,
) : AbstractSubSettingsContext<SonarQubeMeasuresSettings>(
    "sonarqube-measures",
    SonarQubeMeasuresSettings::class,
    settingsManagerService,
    cachedSettingsService,
) {
    override fun adjustNodeBeforeParsing(node: JsonNode): JsonNode =
        node.ifMissing(
            SonarQubeMeasuresSettings::disabled to SonarQubeMeasuresSettings.DEFAULT_DISABLED,
            SonarQubeMeasuresSettings::coverageThreshold to SonarQubeMeasuresSettings.DEFAULT_COVERAGE_THRESHOLD,
            SonarQubeMeasuresSettings::blockerThreshold to SonarQubeMeasuresSettings.DEFAULT_BLOCKER_THRESHOLD,
        )
}