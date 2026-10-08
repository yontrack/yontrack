package net.nemerosa.ontrack.extension.environments

import net.nemerosa.ontrack.model.structure.SignatureActor
import java.time.LocalDateTime

/**
 * Override of an admission rule or of a workflow for a pipeline.
 *
 * @property user Who overrode
 * @property actor The agent behind the override, null for a person
 */
data class SlotAdmissionRuleOverride(
    val user: String,
    val timestamp: LocalDateTime,
    val message: String?,
    val actor: SignatureActor? = null,
)
