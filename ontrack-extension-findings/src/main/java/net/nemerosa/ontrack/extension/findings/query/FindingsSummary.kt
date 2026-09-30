package net.nemerosa.ontrack.extension.findings.query

import net.nemerosa.ontrack.extension.findings.model.FindingSeverity
import net.nemerosa.ontrack.model.structure.Branch

/**
 * Summary of the findings of a project, for its Security section.
 *
 * Every count is by maximum severity, as the filter of the findings is, so that a count and the
 * list it leads to agree.
 *
 * @property open Open findings of the project, by maximum severity, every severity present
 * @property acceptedCount Number of accepted findings of the project
 * @property resolvedCount Number of resolved findings of the project
 * @property branches Branches a finding has been exposed on, resolved or not, the most exposed first
 * @property scanners Names of the scanners which reported the findings of the project, sorted
 */
data class FindingsSummary(
    val open: Map<FindingSeverity, Int>,
    val acceptedCount: Int,
    val resolvedCount: Int,
    val branches: List<FindingsBranchSummary>,
    val scanners: List<String>,
) {
    /**
     * Number of open findings of the project
     */
    val openCount: Int get() = open.values.sum()
}

/**
 * Exposure of the findings of a project on one branch.
 *
 * @property branch Branch
 * @property open Findings open on this branch, by maximum severity, every severity present
 * @property counting Whether this branch counts for the state of the findings of the project:
 * matched by its branch model, and not disabled
 */
data class FindingsBranchSummary(
    val branch: Branch,
    val open: Map<FindingSeverity, Int>,
    val counting: Boolean,
) {
    /**
     * Number of findings open on this branch
     */
    val openCount: Int get() = open.values.sum()
}

/**
 * Summary of the findings of one branch, for the branch page and the dashboards.
 *
 * The state of a finding on a branch rolls up its exposure for all the stamps of the branch, as
 * the filter of the findings on a branch does, so that a count and the list it leads to agree —
 * whether the branch counts for the project or not.
 *
 * @property open Findings open on this branch, by maximum severity, every severity present
 * @property acceptedCount Number of findings accepted on this branch
 * @property resolvedCount Number of findings resolved on this branch
 */
data class BranchFindingsSummary(
    val open: Map<FindingSeverity, Int>,
    val acceptedCount: Int,
    val resolvedCount: Int,
) {
    /**
     * Number of findings open on this branch
     */
    val openCount: Int get() = open.values.sum()

    /**
     * Whether a finding has ever been exposed on this branch, resolved or not. A scan which
     * reports nothing leaves no exposure: a branch with clean scans only has none either.
     */
    val hasExposures: Boolean get() = openCount + acceptedCount + resolvedCount > 0
}
