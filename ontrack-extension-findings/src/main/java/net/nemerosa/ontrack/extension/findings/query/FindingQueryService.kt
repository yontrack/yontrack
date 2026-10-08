package net.nemerosa.ontrack.extension.findings.query

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.findings.model.Finding
import net.nemerosa.ontrack.extension.findings.model.FindingAcceptance
import net.nemerosa.ontrack.extension.findings.model.FindingExposure
import net.nemerosa.ontrack.extension.findings.model.FindingObservation
import net.nemerosa.ontrack.extension.findings.model.FindingState
import net.nemerosa.ontrack.extension.findings.model.RankedFinding
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
     * Summary of the findings of a branch: its open findings by severity, and the number of its
     * accepted and resolved ones.
     *
     * @param branch Branch
     * @param date Day against which the expiry of the acceptances is evaluated
     * @return Summary, `null` for a user who cannot see the findings of the project
     */
    fun getBranchFindingsSummary(
        branch: Branch,
        date: LocalDate = Time.now.toLocalDate(),
    ): BranchFindingsSummary?

    /**
     * Findings having a given external ID, across all the projects, by project name.
     *
     * @param externalId External ID of the findings
     * @param projectIds IDs of the projects to look into, `null` for all of them. Whatever they
     * are, the projects whose findings the user cannot see are left out.
     */
    fun getFindingsByExternalId(externalId: String, projectIds: Collection<Int>? = null): List<Finding>

    /**
     * The external IDs of the findings open in at least one of the given projects, ranked by the
     * number of these projects in which they are open, then by severity, the highest first, then by
     * external ID: which finding hurts the most projects.
     *
     * The state of a finding in its project is the one of [getFindingState]: rolled up from the
     * branches which count only.
     *
     * @param projectIds IDs of the projects to look into. The projects whose findings the user
     * cannot see are left out.
     * @param size Maximum number of external IDs to return, at most [MAX_RANKED_FINDINGS]
     * @param date Day against which the expiry of the acceptances is evaluated
     */
    fun getRankedFindings(
        projectIds: Collection<Int>,
        size: Int = DEFAULT_RANKED_FINDINGS,
        date: LocalDate = Time.now.toLocalDate(),
    ): List<RankedFinding>

    /**
     * The external IDs of the findings of the given projects containing a text, ignoring case,
     * whatever their state: open, accepted or resolved. Ranked as [getRankedFindings] ranks them.
     *
     * @param projectIds IDs of the projects to look into. The projects whose findings the user
     * cannot see are left out.
     * @param text Text the external IDs contain, trimmed. A blank one finds nothing. The LIKE
     * wildcards in it match themselves.
     * @param size Maximum number of external IDs to return, at most [MAX_RANKED_FINDINGS]
     * @param date Day against which the expiry of the acceptances is evaluated
     */
    fun getSearchedFindings(
        projectIds: Collection<Int>,
        text: String,
        size: Int = DEFAULT_RANKED_FINDINGS,
        date: LocalDate = Time.now.toLocalDate(),
    ): List<RankedFinding>

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
     * Exposure of a finding on the branches of its project, resolved or not, by branch then stamp,
     * each saying whether its branch counts toward the state of the finding in the project.
     */
    fun getFindingExposures(finding: Finding): List<FindingExposureView>

    /**
     * Observations of a finding, the most recent first.
     *
     * @param filter Filter on the observations: their branch, their stamp, their time
     */
    fun getFindingObservations(
        finding: Finding,
        offset: Int,
        size: Int,
        filter: FindingObservationFilter = FindingObservationFilter(),
    ): PaginatedList<FindingObservationView>

    /**
     * Periods of an exposure, the oldest first, with their stretches under an acceptance.
     *
     * @param exposure Exposure, as returned by [getFindingExposures]
     * @param date Day against which the expiry of the acceptances is evaluated
     */
    fun getExposurePeriods(
        exposure: FindingExposureView,
        date: LocalDate = Time.now.toLocalDate(),
    ): List<FindingExposurePeriodView>

    /**
     * Where and when a finding was first seen: the start of the earliest period of its exposures.
     * `null` when it has none, or when it cannot be seen.
     */
    fun getFindingFirstSeenIn(finding: Finding): FindingSighting?

    /**
     * Where and when a finding resolved in its project: for a finding
     * [resolved][FindingState.RESOLVED] there, the latest end of a period of its exposures on the
     * branches which count. `null` otherwise.
     *
     * @param date Day against which the expiry of the acceptances is evaluated
     */
    fun getFindingResolvedIn(finding: Finding, date: LocalDate = Time.now.toLocalDate()): FindingSighting?

    /**
     * History of a finding, the most recent first: the periods of its exposures, the changes of
     * acceptance within them, and its observations, grouped between these.
     *
     * @param date Day against which the expiry of the acceptances is evaluated
     */
    fun getFindingHistory(
        finding: Finding,
        offset: Int,
        size: Int,
        date: LocalDate = Time.now.toLocalDate(),
    ): PaginatedList<FindingHistoryEntryView>

    /**
     * Acceptance recorded by the most recent observation of a finding, `null` when this
     * observation carries none or when all the observations have been purged.
     */
    fun getFindingAcceptance(finding: Finding): FindingAcceptance?

    companion object {
        /**
         * Number of ranked findings returned by default
         */
        const val DEFAULT_RANKED_FINDINGS = 20

        /**
         * Maximum number of ranked findings returned, whatever the size asked for
         */
        const val MAX_RANKED_FINDINGS = 100
    }
}

/**
 * Exposure of a finding on a branch, for the scans of one stamp, with the branch and the stamp.
 *
 * @property counts Whether the branch counts toward the state of the finding in its project:
 * matched by the branch model of the project (every branch when it has none), and not disabled
 */
data class FindingExposureView(
    val exposure: FindingExposure,
    val branch: Branch,
    val validationStamp: ValidationStamp,
    val counts: Boolean,
)

/**
 * Observation of a finding, with the finding and the validation run of the scan.
 */
data class FindingObservationView(
    val observation: FindingObservation,
    val finding: Finding,
    val validationRun: ValidationRun,
)
