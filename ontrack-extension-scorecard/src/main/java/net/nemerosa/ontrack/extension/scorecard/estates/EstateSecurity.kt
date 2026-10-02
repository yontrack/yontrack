package net.nemerosa.ontrack.extension.scorecard.estates

import net.nemerosa.ontrack.extension.findings.model.FindingKind

/**
 * What an estate expects of the security scans of its projects, for its security readings.
 *
 * @property expectedKinds Kinds of scan every project must have run, each fresher than
 * [freshnessDays], to be covered. None: any fresh scan covers a project, as with no estate.
 * @property freshnessDays Number of days a scan stays fresh, `null` for the freshness of the
 * settings
 * @property criticalTargetDays Number of days a CRITICAL finding may stay open, `null` for no target
 * @property highTargetDays Number of days a HIGH finding may stay open, `null` for no target
 */
data class EstateSecurity(
    val expectedKinds: List<FindingKind> = emptyList(),
    val freshnessDays: Int? = null,
    val criticalTargetDays: Int? = null,
    val highTargetDays: Int? = null,
)
