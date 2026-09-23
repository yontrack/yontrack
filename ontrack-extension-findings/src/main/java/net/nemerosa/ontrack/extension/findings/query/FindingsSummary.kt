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
 */
data class FindingsBranchSummary(
    val branch: Branch,
    val open: Map<FindingSeverity, Int>,
) {
    /**
     * Number of findings open on this branch
     */
    val openCount: Int get() = open.values.sum()
}
