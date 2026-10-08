package net.nemerosa.ontrack.extension.scm.changelog.assistants

import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.asJsonString
import net.nemerosa.ontrack.model.exceptions.InputException
import net.nemerosa.ontrack.model.security.BuildCreate
import net.nemerosa.ontrack.model.security.GlobalSettings
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.settings.AbstractSettingsManager
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import net.nemerosa.ontrack.model.structure.StructureService
import net.nemerosa.ontrack.model.support.SettingsRepository
import net.nemerosa.ontrack.model.support.setBoolean
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Component

@Component
class AgentMarkersSettingsManager(
    cachedSettingsService: CachedSettingsService,
    private val securityService: SecurityService,
    private val structureService: StructureService,
    private val settingsRepository: SettingsRepository,
) : AbstractSettingsManager<AgentMarkersSettings>(
    AgentMarkersSettings::class.java,
    cachedSettingsService,
    securityService
) {

    /**
     * The markers are read by whoever creates builds, agents included: CI applies them when it
     * sets the assisted change of a build itself, from the trailers of a range of commits (#2044).
     *
     * They decide no permission and hold nothing secret: they are the conventions by which the
     * commits name their assistants. Saving them stays an administration.
     *
     * `projectList` is narrowed to the projects the user can see, and `any` stops at the first
     * project where the user can create builds. `BuildCreate` is in the agent policy, so an agent
     * reads the markers when its owner can create builds somewhere.
     */
    override fun checkRead() {
        val granted = securityService.isGlobalFunctionGranted(GlobalSettings::class.java) ||
                structureService.projectList.any {
                    securityService.isProjectFunctionGranted(it, BuildCreate::class.java)
                }
        if (!granted) {
            throw AccessDeniedException(
                "Reading the agent markers requires the right to create builds on at least one project."
            )
        }
    }

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
