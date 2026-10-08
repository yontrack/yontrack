package net.nemerosa.ontrack.extension.agents.evidence

import net.nemerosa.ontrack.model.exceptions.InputException

/**
 * An agent records, or changes, evidence on a validation stamp whose *Evidence from non-agents only*
 * property refuses it.
 *
 * @param stamp Name of the validation stamp
 */
class NonAgentEvidenceException(stamp: String) : InputException(message(stamp)) {
    companion object {
        /**
         * Reason of the refusal
         */
        fun message(stamp: String) = "evidence on $stamp must come from a non-agent actor"
    }
}
