package net.nemerosa.ontrack.extension.findings.model

/**
 * State of a finding in its project, rolled up from its exposure on the branches which count:
 * the branches matched by the branch model of the project (all of them when it has none), the
 * disabled branches left out.
 *
 * Evaluated when it is read, so that an acceptance past its expiry stops counting without any
 * job.
 */
enum class FindingState {

    /**
     * Exposed, without any acceptance holding, on at least one branch which counts.
     */
    OPEN,

    /**
     * Not open, but exposed under an acceptance which holds on at least one branch which counts.
     * An accepted finding is neither open nor resolved.
     */
    ACCEPTED,

    /**
     * Neither open nor accepted.
     */
    RESOLVED,
}
