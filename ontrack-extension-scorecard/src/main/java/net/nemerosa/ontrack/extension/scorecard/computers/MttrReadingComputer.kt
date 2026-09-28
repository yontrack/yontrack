package net.nemerosa.ontrack.extension.scorecard.computers

import net.nemerosa.ontrack.extension.chart.support.Interval
import net.nemerosa.ontrack.extension.scorecard.engine.DurationStatistics
import net.nemerosa.ontrack.extension.scorecard.engine.PromotionMarker
import net.nemerosa.ontrack.extension.scorecard.engine.ReadingComputer
import net.nemerosa.ontrack.extension.scorecard.engine.ReadingOutcome
import net.nemerosa.ontrack.extension.scorecard.engine.ReadingSubject
import net.nemerosa.ontrack.extension.scorecard.model.ReadingKeys
import net.nemerosa.ontrack.extension.scorecard.model.ReadingUnknownReason
import net.nemerosa.ontrack.extension.scorecard.samples.InFlight
import net.nemerosa.ontrack.extension.scorecard.samples.OutageSample
import net.nemerosa.ontrack.extension.scorecard.samples.Outages
import net.nemerosa.ontrack.extension.scorecard.samples.PromotionSamples
import org.springframework.stereotype.Component

/**
 * `delivery.mttr`: the time it takes to restore the path to the marker once it is broken.
 *
 * Under a promotion marker, the [outages][Outages] of the branches of the marker restored in the
 * window: from the first unpromoted build after a promoted one to the next promotion on that
 * branch. The median, in seconds, goes in the value.
 *
 * With no outage restored in the window, the reading is unknown, never 0:
 *
 * * `NO_FAILURE` when there was nothing to restore;
 * * `NO_SAMPLES` when an outage is still going on — the path is broken, but not restored yet.
 *   An outage started by a build [in flight][InFlight] is not a failure yet.
 */
@Component
class MttrReadingComputer(
    private val promotionSamples: PromotionSamples,
) : ReadingComputer {

    override val key: String = ReadingKeys.DELIVERY_MTTR

    override fun compute(subject: ReadingSubject, window: Interval): ReadingOutcome =
        when (val marker = subject.marker) {
            null -> ReadingOutcome.unknown(ReadingUnknownReason.NO_MARKER)
            is PromotionMarker -> aggregate(
                outages = promotionSamples.outages(marker.levels, window),
                inFlight = InFlight.of(promotionSamples.leadTimes(marker.levels, window), window),
            )
        }

    companion object {
        /**
         * Median of the times to restore in the value, p90, mean, min, max and count in the details.
         *
         * Details also carry the outages still going on at the end of the window (`open`, and the
         * start of the oldest one, `openSince`) and how the builds in flight were left out of them
         * (`inFlight`).
         */
        fun aggregate(outages: List<OutageSample>, inFlight: InFlight): ReadingOutcome {
            val open = outages.filter { it.open }
            val openFailures = open.filterNot { it.start in inFlight }
            val openDetails = mapOf(
                "open" to openFailures.size,
                "openSince" to openFailures.minOfOrNull { it.start },
                "inFlight" to inFlight.details(excluded = open.size - openFailures.size),
            )
            val stats = DurationStatistics.of(outages.mapNotNull { it.timeToRestore?.seconds })
            return when {
                stats != null -> ReadingOutcome.measured(stats.median, stats.details + openDetails)
                openFailures.isNotEmpty() -> ReadingOutcome.unknown(
                    ReadingUnknownReason.NO_SAMPLES,
                    mapOf("count" to 0) + openDetails
                )

                else -> ReadingOutcome.unknown(
                    ReadingUnknownReason.NO_FAILURE,
                    mapOf("count" to 0) + openDetails
                )
            }
        }
    }
}
