package net.nemerosa.ontrack.extension.findings.model

import java.time.LocalDateTime

/**
 * One known weakness — a vulnerability, a code issue, a secret, a DAST alert — at one location
 * of one project, identified by `(scanner, externalId, location)`.
 *
 * A finding references its project only, so that it survives the purge of the builds which
 * observed it. Its times and maximum severity are denormalised from its observations.
 *
 * @property id Database ID (ignored when inserting a new finding)
 * @property projectId ID of the project the finding belongs to
 * @property scanner Name of the scanner (free string: trivy, codeql, zap…)
 * @property externalId Identifier given by the scanner (a CVE, a rule ID…)
 * @property location Where the finding is, without any version. May be empty, never null.
 * @property kind Kind of scan which reported the finding
 * @property title Short description of the finding
 * @property url Link to more information about the finding
 * @property firstSeen Time of the first observation
 * @property lastSeen Time of the last observation
 * @property resolvedAt Time the finding was resolved, if it is
 * @property maxSeverity Maximum severity across the observations
 */
data class Finding(
    val id: Int,
    val projectId: Int,
    val scanner: String,
    val externalId: String,
    val location: String,
    val kind: FindingKind,
    val title: String,
    val url: String?,
    val firstSeen: LocalDateTime,
    val lastSeen: LocalDateTime,
    val resolvedAt: LocalDateTime?,
    val maxSeverity: FindingSeverity,
)
