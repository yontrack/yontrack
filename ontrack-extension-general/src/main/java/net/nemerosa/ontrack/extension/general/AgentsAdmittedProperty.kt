package net.nemerosa.ontrack.extension.general

import net.nemerosa.ontrack.common.api.APIDescription

/**
 * Setting this property on a promotion level admits agents to it: a registered agent may then
 * promote a build to this level, provided its owner may.
 *
 * @property admitted Agents are admitted when the property is set and this flag is true (the default)
 */
@APIDescription("Agents are admitted to this promotion level: a registered agent may promote a build to it, provided its owner may.")
data class AgentsAdmittedProperty(
    @APIDescription("Agents are admitted when the property is set and this flag is true (the default)")
    val admitted: Boolean = true,
)
