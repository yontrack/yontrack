package net.nemerosa.ontrack.extension.findings.ingestion

/**
 * Transition of a finding on a branch, caused by a scan.
 */
enum class FindingExposureTransitionType {

    /**
     * The finding becomes exposed on the branch, without acceptance, where it was not — including
     * a return after resolution or after acceptance.
     */
    NEW,

    /**
     * The finding, exposed on the branch, is no longer reported there.
     */
    RESOLVED,
}
