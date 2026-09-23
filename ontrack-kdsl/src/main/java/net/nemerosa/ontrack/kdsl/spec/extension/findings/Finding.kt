package net.nemerosa.ontrack.kdsl.spec.extension.findings

import net.nemerosa.ontrack.kdsl.connector.graphql.schema.fragment.FindingFragment
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.FindingExposureState
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.FindingKind
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.FindingSeverity
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.FindingState
import java.time.LocalDateTime

/**
 * One known weakness at one location of one project, identified by its scanner, its external ID
 * and its location.
 *
 * @property id Unique ID of the finding
 * @property scanner Name of the scanner which reported the finding
 * @property externalId Identifier given by the scanner, like a CVE or a rule ID
 * @property location Where the finding is, without any version. May be empty.
 * @property kind Kind of scan which reported the finding
 * @property title Short description of the finding
 * @property url Link to more information about the finding
 * @property maxSeverity Maximum severity across the observations of the finding
 * @property state State of the finding in its project
 * @property resolvedAt Time the finding was resolved in its project, if it is
 * @property acceptance Acceptance recorded by the most recent observation of the finding
 * @property exposures Exposure of the finding per branch and validation stamp, resolved or not
 */
data class Finding(
    val id: Int,
    val scanner: String,
    val externalId: String,
    val location: String,
    val kind: FindingKind,
    val title: String,
    val url: String?,
    val maxSeverity: FindingSeverity,
    val state: FindingState?,
    val resolvedAt: LocalDateTime?,
    val acceptance: FindingAcceptance?,
    val exposures: List<FindingExposure>,
) {
    /**
     * Gets the exposure of this finding on a branch.
     *
     * @param branch Name of the branch
     * @return The exposure on this branch (for its only validation stamp), `null` if the finding
     * was never exposed there
     */
    fun exposureOn(branch: String): FindingExposure? = exposures.singleOrNull { it.branch == branch }
}

/**
 * Decision recorded outside Yontrack, read by it, that a finding is tolerated.
 *
 * @property statement Why the finding is tolerated
 * @property source Where the decision is recorded
 * @property expiresAt Last day the acceptance holds, as an ISO date, if any
 * @property effective Whether the acceptance holds today
 */
data class FindingAcceptance(
    val statement: String?,
    val source: String?,
    val expiresAt: String?,
    val effective: Boolean,
)

/**
 * Exposure of a finding on a branch, for the scans of one validation stamp.
 *
 * @property branch Name of the branch
 * @property validationStamp Name of the validation stamp of the scans
 * @property state State of the exposure today
 * @property accepted Whether the latest scan reported the finding under an acceptance
 * @property resolvedAt Time the finding was resolved on this branch for this stamp, if it is
 */
data class FindingExposure(
    val branch: String,
    val validationStamp: String,
    val state: FindingExposureState,
    val accepted: Boolean,
    val resolvedAt: LocalDateTime?,
)

/**
 * One sighting of a finding by one scan.
 *
 * @property severity Severity asserted by the scanner in this observation
 * @property rawSeverity Severity as the scanner gave it
 * @property installedVersion Version of the component in which the finding was observed
 * @property fixedVersion Version of the component fixing the finding, if any
 * @property finding Observed finding
 */
data class FindingObservation(
    val severity: FindingSeverity,
    val rawSeverity: String?,
    val installedVersion: String?,
    val fixedVersion: String?,
    val finding: Finding,
)

internal fun FindingFragment.toFinding() = Finding(
    id = id,
    scanner = scanner,
    externalId = externalId,
    location = location,
    kind = kind,
    title = title,
    url = url,
    maxSeverity = maxSeverity,
    state = state,
    resolvedAt = resolvedAt,
    acceptance = acceptance?.let {
        FindingAcceptance(
            statement = it.statement,
            source = it.source,
            expiresAt = it.expiresAt,
            effective = it.effective,
        )
    },
    exposures = exposures.map {
        FindingExposure(
            branch = it.branch.name!!,
            validationStamp = it.validationStamp.name!!,
            state = it.state,
            accepted = it.accepted,
            resolvedAt = it.resolvedAt,
        )
    },
)
