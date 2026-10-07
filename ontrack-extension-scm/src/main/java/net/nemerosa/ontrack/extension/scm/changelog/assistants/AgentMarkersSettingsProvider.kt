package net.nemerosa.ontrack.extension.scm.changelog.assistants

import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.settings.SettingsProvider
import net.nemerosa.ontrack.model.support.SettingsRepository
import net.nemerosa.ontrack.model.support.getBoolean
import org.springframework.stereotype.Component

@Component
class AgentMarkersSettingsProvider(
    private val settingsRepository: SettingsRepository,
) : SettingsProvider<AgentMarkersSettings> {

    override fun getSettings() = AgentMarkersSettings(
        builtInConventions = settingsRepository.getBoolean(
            AgentMarkersSettings::builtInConventions,
            true
        ),
        patterns = parsePatterns(
            settingsRepository.getString(
                AgentMarkersSettings::class.java,
                AgentMarkersSettings::patterns.name,
                ""
            )
        ),
    )

    private fun parsePatterns(json: String): List<AgentMarkerPattern> =
        if (json.isBlank()) {
            emptyList()
        } else {
            json.parseAsJson().values().map { node -> node.parse<AgentMarkerPattern>() }
        }

    override fun getSettingsClass(): Class<AgentMarkersSettings> = AgentMarkersSettings::class.java
}
