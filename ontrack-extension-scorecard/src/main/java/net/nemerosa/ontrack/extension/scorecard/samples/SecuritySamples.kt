package net.nemerosa.ontrack.extension.scorecard.samples

import net.nemerosa.ontrack.extension.chart.support.Interval
import net.nemerosa.ontrack.extension.findings.model.FindingKind
import net.nemerosa.ontrack.model.structure.Branch
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
