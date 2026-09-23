package net.nemerosa.ontrack.extension.findings.repository

import net.nemerosa.ontrack.extension.findings.model.Finding
import net.nemerosa.ontrack.extension.findings.model.FindingExposure
import net.nemerosa.ontrack.extension.findings.model.FindingObservation

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
     * Gets a finding by its key in a project.
     */
    fun findFindingByKey(projectId: Int, scanner: String, externalId: String, location: String): Finding?

    /**
     * Gets all the findings of a project.
     */
    fun findFindingsByProject(projectId: Int): List<Finding>

    /**
     * Gets all the findings having the given external ID, across all projects.
     */
    fun findFindingsByExternalId(externalId: String): List<Finding>

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

    // Exposure

    /**
     * Inserts exposures, as one JDBC batch.
     */
    fun insertExposures(exposures: List<FindingExposure>)

    /**
     * Gets the exposure of a finding, on all branches.
     */
    fun findExposuresByFinding(findingId: Int): List<FindingExposure>

    /**
     * Gets the exposure on a branch, for the scans of one validation stamp.
     */
    fun findExposuresByBranchAndStamp(branchId: Int, validationStampId: Int): List<FindingExposure>

    /**
     * Removes the exposure of some findings on a branch, for the scans of one validation stamp.
     */
    fun deleteExposures(branchId: Int, validationStampId: Int, findingIds: Collection<Int>)
}
