package net.nemerosa.ontrack.extension.agents.evidence

import net.nemerosa.ontrack.extension.agents.license.AgentsLicense
import net.nemerosa.ontrack.model.security.AgentEvidenceCheck
import net.nemerosa.ontrack.model.structure.PropertyService
import net.nemerosa.ontrack.model.structure.SignatureActor
import net.nemerosa.ontrack.model.structure.ValidationStamp
import org.springframework.stereotype.Component

/**
 * Applies *Evidence from non-agents only*: an agent's run, status change or data change on the stamp
 * is refused - never accepted and then ignored, so that a run on the record always counts.
 *
 * Without the licence of the agent governance, the property does nothing.
 */
@Component
class NonAgentEvidenceCheck(
    private val agentsLicense: AgentsLicense,
    private val propertyService: PropertyService,
) : AgentEvidenceCheck {

    override fun checkAgentEvidence(validationStamp: ValidationStamp, agent: SignatureActor) {
        if (isRestricted(validationStamp) && agentsLicense.agentsEnabled) {
            throw NonAgentEvidenceException(validationStamp.name)
        }
    }

    private fun isRestricted(validationStamp: ValidationStamp): Boolean =
        propertyService.getPropertyValue(validationStamp, NonAgentEvidencePropertyType::class.java)
            ?.enabled
            ?: false
}
