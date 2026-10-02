package net.nemerosa.ontrack.extension.findings.model

import java.time.LocalDateTime

/**
 * One external ID of the findings of some projects, ranked by the number of these projects in
 * which it is open: what hurts the most projects of a group.
 *
 * Each project is counted once, by the state of its most exposed finding having this external ID
 * — one per scanner and location: open, else accepted, else resolved.
 *
 * @property externalId External ID of the findings, like a CVE or a rule ID
 * @property title Title of the findings, the one of the most exposed then most severe of them
 * @property severity Highest maximum severity of the findings
 * @property openProjects Number of projects in which the finding is open
 * @property acceptedProjects Number of projects in which the finding is accepted, and not open
 * @property resolvedProjects Number of projects in which the finding is neither open nor accepted
 * @property firstSeen Earliest time any of the findings was first seen
 */
data class RankedFinding(
    val externalId: String,
    val title: String,
    val severity: FindingSeverity,
    val openProjects: Int,
    val acceptedProjects: Int,
    val resolvedProjects: Int,
    val firstSeen: LocalDateTime,
)
