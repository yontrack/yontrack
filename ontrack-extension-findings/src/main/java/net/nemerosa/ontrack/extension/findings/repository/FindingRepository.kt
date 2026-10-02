package net.nemerosa.ontrack.extension.findings.repository

import net.nemerosa.ontrack.extension.findings.model.Finding
import net.nemerosa.ontrack.extension.findings.model.FindingExposure
import net.nemerosa.ontrack.extension.findings.model.FindingObservation
import net.nemerosa.ontrack.extension.findings.model.FindingSeverity
import net.nemerosa.ontrack.extension.findings.model.FindingState
import net.nemerosa.ontrack.extension.findings.model.RankedFinding
import java.time.LocalDate

/**
 * Storage of the findings, their observations and their exposure.
 *
 * No security check is done at this level.
 */
interface FindingRepository {

    // Findings

    /**
     * Inserts a new finding.
     *
     * @param finding Finding to insert. Its ID is ignored.
     * @return The finding with its assigned ID
     */
    fun insertFinding(finding: Finding): Finding

    /**
     * Updates the mutable fields of a finding, identified by its ID: kind, title, URL, last seen,
     * resolution time and maximum severity. Its key and its first time seen never change.
     */
    fun updateFinding(finding: Finding)

    /**
     * Gets a finding by ID.
     */
    fun findFindingById(id: Int): Finding?

    /**
     * Gets findings by ID.
     */
    fun findFindingsByIds(ids: Collection<Int>): List<Finding>

    /**
     * Gets a finding by its key in a project.
     */
    fun findFindingByKey(projectId: Int, scanner: String, externalId: String, location: String): Finding?

    /**
     * Gets all the findings of a project.
     */
    fun findFindingsByProject(projectId: Int): List<Finding>

    /**
     * Gets all the findings of a project reported by a given scanner.
     */
    fun findFindingsByProjectAndScanner(projectId: Int, scanner: String): List<Finding>

    /**
     * Gets all the findings having the given external ID, across all projects, or across the
     * given projects only.
     *
     * @param externalId External ID of the findings
     * @param projectIds IDs of the projects to look into, `null` for all of them
     */
    fun findFindingsByExternalId(externalId: String, projectIds: Collection<Int>? = null): List<Finding>

    /**
     * The external IDs of the findings open in at least one of the given projects, ranked by the
     * number of these projects in which they are open, then by severity, the highest first, then
     * by external ID — aggregated by the database.
     *
     * The state of a finding in its project is rolled up from its exposure on the branches which
     * count only, as [FindingState] says; a project is counted once per external ID, by its most
     * exposed finding.
     *
     * @param countingBranchIds IDs of the branches which count, for each project to look into, by
     * project ID
     * @param date Day against which the expiry of the acceptances is evaluated
     * @param size Maximum number of external IDs to return
     */
    fun findRankedFindings(
        countingBranchIds: Map<Int, Set<Int>>,
        date: LocalDate,
        size: Int,
    ): List<RankedFinding>

    /**
     * The external IDs of the findings of the given projects containing a text, ignoring case,
     * whatever their state, ranked as [findRankedFindings] ranks them: by the number of these
     * projects in which they are open, then by severity, the highest first, then by external ID —
     * aggregated by the database.
     *
     * @param countingBranchIds IDs of the branches which count, for each project to look into, by
     * project ID
     * @param text Text the external IDs contain, ignoring case. The LIKE wildcards in it match
     * themselves.
     * @param date Day against which the expiry of the acceptances is evaluated
     * @param size Maximum number of external IDs to return
     */
    fun findSearchedFindings(
        countingBranchIds: Map<Int, Set<Int>>,
        text: String,
        date: LocalDate,
        size: Int,
    ): List<RankedFinding>

    /**
     * Goes through all the findings, of all the projects, by ID.
     */
    fun forEachFinding(code: (Finding) -> Unit)

    // Observations

    /**
     * Inserts observations, as one JDBC batch.
     */
    fun insertObservations(observations: List<FindingObservation>)

    /**
     * Gets the observations of a finding, the most recent first.
     */
    fun findObservationsByFinding(findingId: Int): List<FindingObservation>

    /**
     * Gets the observations made by a validation run.
     */
    fun findObservationsByValidationRun(validationRunId: Int): List<FindingObservation>

    /**
     * Gets the severity of the latest observation of some findings by the runs of a validation
     * stamp. A finding whose observations were all purged has none.
     *
     * @return Severity per finding ID
     */
    fun findLatestSeverities(validationStampId: Int, findingIds: Collection<Int>): Map<Int, FindingSeverity>

    // Exposure

    /**
     * Saves exposures, as one JDBC batch: a row is created, or replaced when one exists for the
     * same finding, branch and stamp.
     */
    fun saveExposures(exposures: List<FindingExposure>)

    /**
     * Gets the exposure of a finding, on all branches, resolved or not.
     */
    fun findExposuresByFinding(findingId: Int): List<FindingExposure>

    /**
     * Gets the exposure of some findings, on all branches, resolved or not.
     */
    fun findExposuresByFindings(findingIds: Collection<Int>): List<FindingExposure>

    /**
     * Gets the exposure on a branch, for all its stamps, resolved or not.
     */
    fun findExposuresByBranch(branchId: Int): List<FindingExposure>

    /**
     * Gets the exposure on a branch, for the scans of one validation stamp, resolved or not.
     */
    fun findExposuresByBranchAndStamp(branchId: Int, validationStampId: Int): List<FindingExposure>
}
