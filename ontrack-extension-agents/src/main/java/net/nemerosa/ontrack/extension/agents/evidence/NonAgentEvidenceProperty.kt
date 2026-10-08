package net.nemerosa.ontrack.extension.agents.evidence

import net.nemerosa.ontrack.common.api.APIDescription

/**
 * *Evidence from non-agents only*, on a validation stamp: an agent may neither create a validation
 * run on the stamp nor change the status of one of its runs.
 *
 * Its presence is what counts; the flag is there so that the property can be set without any other
 * value, and so that it can be kept while switched off.
 *
 * @property enabled The restriction applies when the property is set and this flag is true (the default)
 */
@APIDescription("Evidence on this validation stamp must come from a non-agent actor: an agent may neither create a validation run on it nor change the status of one of its runs.")
data class NonAgentEvidenceProperty(
    @APIDescription("The restriction applies when the property is set and this flag is true (the default)")
    val enabled: Boolean = true,
)
