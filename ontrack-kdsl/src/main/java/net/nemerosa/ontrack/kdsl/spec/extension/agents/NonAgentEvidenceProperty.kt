package net.nemerosa.ontrack.kdsl.spec.extension.agents

import net.nemerosa.ontrack.kdsl.spec.ValidationStamp
import net.nemerosa.ontrack.kdsl.spec.deleteProperty
import net.nemerosa.ontrack.kdsl.spec.getProperty
import net.nemerosa.ontrack.kdsl.spec.setProperty

const val NON_AGENT_EVIDENCE_PROPERTY =
    "net.nemerosa.ontrack.extension.agents.evidence.NonAgentEvidencePropertyType"

/**
 * *Evidence from non-agents only* on a validation stamp: when true, an agent may neither create a run
 * on the stamp nor change the status of one of its runs (with the licence of the agent governance).
 * Setting it to false removes the property.
 */
var ValidationStamp.nonAgentEvidence: Boolean
    get() = getProperty(NON_AGENT_EVIDENCE_PROPERTY)
        ?.takeIf { it.isObject }
        ?.path("enabled")
        ?.let { !it.isBoolean || it.booleanValue() }
        ?: false
    set(value) {
        if (value) {
            setProperty(NON_AGENT_EVIDENCE_PROPERTY, mapOf("enabled" to true))
        } else {
            deleteProperty(NON_AGENT_EVIDENCE_PROPERTY)
        }
    }
