package net.nemerosa.ontrack.extension.scorecard.samples

import net.nemerosa.ontrack.extension.chart.support.Interval
import net.nemerosa.ontrack.extension.scorecard.engine.DurationStatistics
import java.time.LocalDateTime

/**
 * Builds in flight at the end of an interval: created within the median lead time of the interval
 * before its end. They have not had the time a build usually takes to reach the marker, so their
 * not being promoted says nothing yet — they are left out of the success rate, and an outage they
 * start is not counted as a failure yet.
 *
 * With no lead time in the interval, nothing is in flight.
 *
 * @property leadTime Median lead time of the interval, in seconds, `null` when there is none
 * @property since Builds created at or after this time are in flight
 */
data class InFlight(
    val leadTime: Double?,
    val since: LocalDateTime,
) {

    /**
     * Is a build created at this time in flight?
     */
    operator fun contains(creation: LocalDateTime): Boolean = creation >= since

    /**
     * How the exclusion was made, for the details of a reading
     *
     * @param excluded Number of builds (or outages) left out because in flight
     */
    fun details(excluded: Int): Map<String, Any?> = mapOf(
        "leadTime" to leadTime,
        "since" to since,
        "excluded" to excluded,
    )

    companion object {

        /**
         * Builds in flight at the end of the interval, from the lead times of the interval.
         *
         * @param leadTimes Lead times of the interval, as returned by [PromotionSamples.leadTimes]
         * @param interval Interval whose end is the reference
         */
        fun of(leadTimes: List<DurationSample>, interval: Interval): InFlight {
            val median = DurationStatistics.of(leadTimes.map { it.seconds })?.median
            return InFlight(
                leadTime = median,
                since = median?.let { interval.end.minusSeconds(it.toLong()) } ?: interval.end,
            )
        }
    }
}
