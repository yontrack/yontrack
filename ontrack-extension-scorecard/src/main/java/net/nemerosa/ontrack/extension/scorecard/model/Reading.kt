package net.nemerosa.ontrack.extension.scorecard.model

import tools.jackson.databind.JsonNode
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * One reading of one project, for one set, on one day: the snapshot stored by the daily computation.
 *
 * @property estateId Estate of the set, `null` for the no-estate set
 * @property projectId Project which was read
 * @property key Reading key, like `delivery.leadTime` (see [ReadingKeys])
 * @property day Day of the snapshot. A recompute on the same day overwrites it.
 * @property computedAt When the reading was computed
 * @property windowStart Start of the window the reading was taken over
 * @property windowEnd End of the window the reading was taken over
 * @property value Value of the reading, `null` when [basis] is [ReadingBasis.UNKNOWN]
 * @property basis What the value rests on
 * @property unknownReason Why the value is unknown, set only when [basis] is [ReadingBasis.UNKNOWN]
 * @property details Everything which explains the value: the sample count, the scope, the marker...
 */
data class Reading(
    val estateId: Int?,
    val projectId: Int,
    val key: String,
    val day: LocalDate,
    val computedAt: LocalDateTime,
    val windowStart: LocalDateTime,
    val windowEnd: LocalDateTime,
    val value: Double?,
    val basis: ReadingBasis,
    val unknownReason: ReadingUnknownReason?,
    val details: JsonNode,
)
