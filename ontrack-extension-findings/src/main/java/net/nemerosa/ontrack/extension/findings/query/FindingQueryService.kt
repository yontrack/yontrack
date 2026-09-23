package net.nemerosa.ontrack.extension.findings.query

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.findings.model.Finding
import net.nemerosa.ontrack.extension.findings.model.FindingAcceptance
import net.nemerosa.ontrack.extension.findings.model.FindingExposure
import net.nemerosa.ontrack.extension.findings.model.FindingObservation
import net.nemerosa.ontrack.extension.findings.model.FindingState
import net.nemerosa.ontrack.model.pagination.PaginatedList
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.ValidationRun
import net.nemerosa.ontrack.model.structure.ValidationStamp
import java.time.LocalDate

/**
 * Reading the findings.
 *
 * Every read is filtered by
 * [ProjectFindingsView][net.nemerosa.ontrack.extension.findings.security.ProjectFindingsView]:
 * a user who cannot see the findings of a project gets none of them — an empty list, not an
 * error — and a user who cannot see the project at all gets nothing either.
 */
interface FindingQueryService {

    /**
     * Findings of a project, the most severe first, then the most recently seen.
     *
     * @param project Project
     * @param filter Filter on the findings
     * @param offset Index of the first finding to return
     * @param size Maximum number of findings to return
     * @param date Day against which the expiry of the acceptances is evaluated
     */
    fun getProjectFindings(
        project: Project,
        filter: FindingFilter,
        offset: Int,
        size: Int,
        date: LocalDate = Time.now.toLocalDate(),
    ): PaginatedList<Finding>

    /**
     * Summary of the findings of a project: its open findings by severity, and their exposure
     * per branch.
     *
     * @param project Project
     * @param date Day against which the expiry of the acceptances is evaluated
     * @return Summary, `null` for a user who cannot see the findings of the project
     */
    fun getProjectFindingsSummary(
        project: Project,
        date: LocalDate = Time.now.toLocalDate(),
    ): FindingsSummary?

    /**
     * Findings having a given external ID, across all the projects, by project name.
     */
    fun getFindingsByExternalId(externalId: String): List<Finding>

    /**
     * Finding by ID, `null` when it does not exist or cannot be seen.
     */
    fun findFindingById(id: Int): Finding?

    /**
     * Findings reported by a validation run, with their observation by this run, the most severe
     * first. Empty for a run which is not a security scan.
     */
    fun getValidationRunFindings(run: ValidationRun): List<FindingObservationView>

    /**
     * State of a finding in its project.
     */
    fun getFindingState(finding: Finding, date: LocalDate = Time.now.toLocalDate()): FindingState?

    /**
     * Exposure of a finding on the branches of its project, resolved or not, by branch then stamp.
     */
    fun getFindingExposures(finding: Finding): List<FindingExposureView>

    /**
     * Observations of a finding, the most recent first.
     */
    fun getFindingObservations(finding: Finding, offset: Int, size: Int): PaginatedList<FindingObservationView>

    /**
     * Acceptance recorded by the most recent observation of a finding, `null` when this
     * observation carries none or when all the observations have been purged.
     */
    fun getFindingAcceptance(finding: Finding): FindingAcceptance?
}

/**
 * Exposure of a finding on a branch, for the scans of one stamp, with the branch and the stamp.
 */
data class FindingExposureView(
    val exposure: FindingExposure,
    val branch: Branch,
    val validationStamp: ValidationStamp,
)

/**
 * Observation of a finding, with the finding and the validation run of the scan.
 */
data class FindingObservationView(
    val observation: FindingObservation,
    val finding: Finding,
    val validationRun: ValidationRun,
)
