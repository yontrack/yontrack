package net.nemerosa.ontrack.extension.findings.model

/**
 * Why a finding was resolved on a branch.
 *
 * Extensible: a `COMPONENT_REMOVED` reason is left for the day an SBOM can tell a component gone
 * from a finding absent.
 */
enum class FindingResolutionReason {

    /**
     * The latest scan of the stamp on the branch does not report the finding.
     */
    ABSENT,
}
