package net.nemerosa.ontrack.model.security

import net.nemerosa.ontrack.model.structure.SignatureActor
import net.nemerosa.ontrack.model.structure.ValidationStamp

/**
 * Says whether an agent may record evidence on a validation stamp.
 *
 * Called before every validation run is created, before a status is added to a run, and before the
 * data of a run is changed - each time an agent is the actor, that is, the agent itself or the agent
 * the system acts on behalf of. Every way of recording evidence goes through these three, so that no
 * mutation, ingestion or listener needs to check it on its own.
 *
 * Implemented by the *Evidence from non-agents only* property of the agents extension. Without any
 * implementation, agents record evidence like anybody else.
 */
interface AgentEvidenceCheck {

    /**
     * Throws when the [agent] may not record, nor change, evidence on the [validationStamp].
     */
    fun checkAgentEvidence(validationStamp: ValidationStamp, agent: SignatureActor)

}
