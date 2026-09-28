package net.nemerosa.ontrack.kdsl.spec.extension.scorecard

import net.nemerosa.ontrack.kdsl.connector.graphql.schema.fragment.ReadingFragment
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.ReadingBasis
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.ReadingDirection
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.ReadingUnknownReason
import tools.jackson.databind.JsonNode
import java.time.LocalDateTime

/**
 * Delivery scorecard of a project: its readings in every set it is in.
 *
 * @property sets The set with no estate first, always present, then the set of each estate the
 * project belongs to, by name — the latter only when the licence allows the estates
 */
data class Scorecard(
    val sets: List<ScorecardSet>,
) {
    /**
     * The set with no estate: the project read on its own, up to the last promotion level of each
     * branch, with no target.
     */
    val noEstate: ScorecardSet
        get() = sets.first { it.estate == null }

    /**
     * The set of an estate, `null` when the project does not belong to it.
     *
     * @param name Name of the estate
     */
    fun estate(name: String): ScorecardSet? = sets.firstOrNull { it.estate == name }
}

/**
 * The readings of a project in one set.
 *
 * @property name Name of the set: `Project` for the set with no estate, the name of the estate otherwise
 * @property estate Name of the estate of the set, `null` for the set with no estate
 * @property readings Latest snapshot of each reading, in the catalogue order. Empty until computed.
 */
data class ScorecardSet(
    val name: String,
    val estate: String?,
    val readings: List<Reading>,
) {
    /**
     * Latest snapshot of a reading, `null` when not computed yet.
     *
     * @param key Key of the reading, see [ReadingKeys]
     */
    fun reading(key: String): Reading? = readings.firstOrNull { it.key == key }

    /**
     * When the readings of the set were last computed, `null` when they never were.
     */
    val computedAt: LocalDateTime?
        get() = readings.maxOfOrNull { it.computedAt }

    /**
     * Whether the readings of this set were computed after a moment, or at all when there is none.
     */
    internal fun isComputedAfter(moment: LocalDateTime?): Boolean {
        val last = computedAt
        return last != null && (moment == null || last > moment)
    }
}

/**
 * One measurement of one project at one moment, for one set.
 *
 * @property key Key of the reading, see [ReadingKeys]
 * @property day Day of the snapshot, as an ISO date
 * @property computedAt When the reading was computed
 * @property windowStart Start of the window the reading was taken over
 * @property windowEnd End of the window the reading was taken over
 * @property value Value, `null` when unknown. Durations in seconds, frequencies per week, rates from 0 to 100.
 * @property basis What the value rests on
 * @property unknownReason Why the reading is unknown, `null` when it is not
 * @property details What explains the value: `count`, the other statistics of a duration (`p90`,
 * `mean`, `min`, `max`), `markerKind`, `marker` and `scope`, and the details proper to each reading
 * @property direction Which way the reading is better
 * @property target Target of the estate for this reading, `null` with no estate or no target
 * @property targetMet Whether the target is met, `null` when the reading is not judged
 * @property history Daily snapshots, oldest first, this one included — only when asked for
 */
data class Reading(
    val key: String,
    val day: String,
    val computedAt: LocalDateTime,
    val windowStart: LocalDateTime,
    val windowEnd: LocalDateTime,
    val value: Double?,
    val basis: ReadingBasis,
    val unknownReason: ReadingUnknownReason?,
    val details: JsonNode?,
    val direction: ReadingDirection?,
    val target: Double?,
    val targetMet: Boolean?,
    val history: List<Reading> = emptyList(),
)

internal fun ReadingFragment.toReading(history: List<Reading> = emptyList()) = Reading(
    key = key,
    day = day,
    computedAt = computedAt,
    windowStart = windowStart,
    windowEnd = windowEnd,
    value = value,
    basis = basis,
    unknownReason = unknownReason,
    details = details,
    direction = direction,
    target = target,
    targetMet = targetMet,
    history = history,
)
