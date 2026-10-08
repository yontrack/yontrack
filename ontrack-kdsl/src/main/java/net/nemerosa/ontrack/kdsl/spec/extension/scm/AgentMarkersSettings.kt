package net.nemerosa.ontrack.kdsl.spec.extension.scm

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import net.nemerosa.ontrack.kdsl.spec.settings.SettingsInterface
import net.nemerosa.ontrack.kdsl.spec.settings.SettingsMgt

/**
 * Agent markers settings: how the commits name the assistants which helped write them.
 *
 * @property builtInConventions Whether the built-in conventions are applied
 * @property patterns Custom patterns
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class AgentMarkersSettings(
    val builtInConventions: Boolean = true,
    val patterns: List<AgentMarkerPattern> = emptyList(),
)

/**
 * Custom pattern of the agent markers.
 *
 * @property name Name of the assistant
 * @property type `CO_AUTHOR_EMAIL`, `AUTHOR_EMAIL`, `TRAILER` or `LOGIN`
 * @property value Regular expression for the emails and the logins, key for a trailer
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class AgentMarkerPattern(
    val name: String,
    val type: String,
    val value: String,
)

/**
 * Agent markers settings: read by the administrators and by whoever creates builds, agents
 * included; saved by the administrators.
 */
val SettingsMgt.agentMarkers: SettingsInterface<AgentMarkersSettings>
    get() = SettingsInterface(
        connector = connector,
        id = "agent-markers",
        type = AgentMarkersSettings::class,
    )
