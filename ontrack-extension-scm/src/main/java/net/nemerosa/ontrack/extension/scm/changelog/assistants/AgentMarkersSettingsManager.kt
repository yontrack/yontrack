package net.nemerosa.ontrack.extension.scm.changelog.assistants

import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.asJsonString
import net.nemerosa.ontrack.model.exceptions.InputException
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.settings.AbstractSettingsManager
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import net.nemerosa.ontrack.model.support.SettingsRepository
import net.nemerosa.ontrack.model.support.setBoolean
import org.springframework.stereotype.Component

@Component
class AgentMarkersSettingsManager(
    cachedSettingsService: CachedSettingsService,
    securityService: SecurityService,
    private val settingsRepository: SettingsRepository,
) : AbstractSettingsManager<AgentMarkersSettings>(
    AgentMarkersSettings::class.java,
    cachedSettingsService,
    securityService
) {

    override fun doSaveSettings(settings: AgentMarkersSettings) {
        val problems = validateAgentMarkerPatterns(settings.patterns)
        if (problems.isNotEmpty()) {
            throw AgentMarkersSettingsException(problems.joinToString("\n"))
        }
        settingsRepository.setBoolean<AgentMarkersSettings>(settings::builtInConventions)
        settingsRepository.setString(
            AgentMarkersSettings::class.java,
            AgentMarkersSettings::patterns.name,
            settings.patterns.map {
                it.copy(name = it.name.trim(), value = it.value.trim())
            }.asJson().asJsonString()
        )
    }

    override fun getId(): String = "agent-markers"

    override fun getTitle(): String = "Agent markers"
}

class AgentMarkersSettingsException(message: String) : InputException(message)
