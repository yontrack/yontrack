package net.nemerosa.ontrack.extension.scorecard.samples

import net.nemerosa.ontrack.extension.chart.support.Interval
import net.nemerosa.ontrack.extension.findings.model.FindingKind
import net.nemerosa.ontrack.extension.findings.model.FindingSeverity
import net.nemerosa.ontrack.extension.findings.model.FindingState
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.Project
import java.time.LocalDateTime

/**
 * Sample functions of the security readings, read on the branches in scope whatever the marker.
 *
 * A scan is a `security-findings` run: a validation run whose data is the one of the security
 * findings (`FindingsValidationDataType`), whatever the data type of its stamp. A security stamp
 * is a stamp with this data type, or one which has received such a run.
 */
interface SecuritySamples {

    /**
     * Security stamps of the branches, ordered by branch and stamp order.
     */
    fun securityStamps(branches: Collection<Branch>): List<SecurityStamp>

    /**
     * Scans of the branches created in the interval, the start of the interval included, its end
     * excluded, ordered by time.
     */
    fun scans(branches: Collection<Branch>, interval: Interval): List<SecurityRunSample>

    /**
     * CRITICAL and HIGH findings of a project — by maximum severity — which matter to its
     * remediation over the interval: the ones resolved at project level from the start of the
     * interval, and the ones not resolved, with their state at the end of the interval on the
     * branches in scope. Ordered by ID.
     *
     * The findings of a project belong to the project, not to a branch: a finding is resolved at
     * project level once no branch which counts for the project exposes it any more. Its location
     * is versionless, so that bumping a package to a version which is still vulnerable does not
     * resolve it.
     *
     * @param project Project of the findings
     * @param branches Branches in scope, which give the state of the findings not resolved
     * @param interval Window of the reading. The state is evaluated on the day of its end, for the
     * expiry of the acceptances.
     */
    fun remediationFindings(
        project: Project,
        branches: Collection<Branch>,
        interval: Interval,
    ): List<SecurityFindingSample>
}

/**
 * A CRITICAL or HIGH finding, for the remediation readings.
 *
 * @property findingId ID of the finding
 * @property severity Maximum severity of the finding across its observations
 * @property firstSeen Time of its first observation
 * @property resolvedAt Time it was resolved at project level, `null` while it is not
 * @property state State of the finding at the end of the window: `RESOLVED` when it has a
 * resolution time, else rolled up from its exposure on the branches in scope — `OPEN`, `ACCEPTED`,
 * or `RESOLVED` when no branch in scope exposes it
 */
data class SecurityFindingSample(
    val findingId: Int,
    val severity: FindingSeverity,
    val firstSeen: LocalDateTime,
    val resolvedAt: LocalDateTime?,
    val state: FindingState,
)

/**
 * Remediation targets of a set: how many days a finding of each severity may stay open, `null`
 * for no target. The no-estate set has none.
 *
 * @property criticalDays Number of days a CRITICAL finding may stay open
 * @property highDays Number of days a HIGH finding may stay open
 */
data class SecurityTargets(
    val criticalDays: Int?,
    val highDays: Int?,
) {
    companion object {
        /**
         * No target at all, as for the no-estate set
         */
        val NONE = SecurityTargets(criticalDays = null, highDays = null)
    }
}

/**
 * A security stamp: a validation stamp receiving `security-findings` runs.
 *
 * @property id ID of the stamp
 * @property branchId Branch of the stamp
 * @property name Name of the stamp
 */
data class SecurityStamp(
    val id: Int,
    val branchId: Int,
    val name: String,
)

/**
 * A scan: one `security-findings` run.
 *
 * @property branchId Branch of the run
 * @property stampId Stamp of the run
 * @property runId ID of the run
 * @property status Status the run was created with, the one the thresholds of its stamp gave
 * @property time Time the run was created
 * @property kinds Kind of the scan; for a scan posted before the kind was recorded on the run,
 * the kinds of the findings it observed, none for such a scan which observed none
 */
data class SecurityRunSample(
    val branchId: Int,
    val stampId: Int,
    val runId: Int,
    val status: String,
    val time: LocalDateTime,
    val kinds: Set<FindingKind>,
)

/**
 * What the security readings of a set expect of the scans of a project.
 *
 * @property expectedKinds Kinds of scan every one of which must be fresh for the project to be
 * covered; none to be covered by any fresh scan
 * @property freshnessDays Number of days a scan stays fresh
 */
data class SecurityCoverage(
    val expectedKinds: Set<FindingKind>,
    val freshnessDays: Int,
)
