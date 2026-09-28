package net.nemerosa.ontrack.extension.scorecard.computers

import net.nemerosa.ontrack.extension.chart.support.Interval
import net.nemerosa.ontrack.extension.scorecard.engine.DurationStatistics
import net.nemerosa.ontrack.extension.scorecard.engine.EnvironmentMarker
import net.nemerosa.ontrack.extension.scorecard.engine.PromotionMarker
import net.nemerosa.ontrack.extension.scorecard.engine.ReadingComputer
import net.nemerosa.ontrack.extension.scorecard.engine.ReadingOutcome
import net.nemerosa.ontrack.extension.scorecard.engine.ReadingSubject
import net.nemerosa.ontrack.extension.scorecard.model.ReadingKeys
import net.nemerosa.ontrack.extension.scorecard.model.ReadingUnknownReason
import net.nemerosa.ontrack.extension.scorecard.samples.DeploymentOutages
import net.nemerosa.ontrack.extension.scorecard.samples.EnvironmentSamples
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
 * branch. Under an environment marker, the [outages][DeploymentOutages] of the slot restored in
 * the window: from a failed deployment to the next one done — the time to restore the deployment
 * path, not an incident's. The median, in seconds, goes in the value.
 *
 * With no outage restored in the window, the reading is unknown, never 0:
 *
 * * `NO_FAILURE` when there was nothing to restore;
 * * `NO_SAMPLES` when an outage is still going on — the path is broken, but not restored yet.
 *   Under a promotion marker, an outage started by a build [in flight][InFlight] is not a failure
 *   yet. A failed deployment is a failure at once.
 */
@Component
class MttrReadingComputer(
    private val promotionSamples: PromotionSamples,
    private val environmentSamples: EnvironmentSamples,
) : ReadingComputer {

    override val key: String = ReadingKeys.DELIVERY_MTTR

    override fun compute(subject: ReadingSubject, window: Interval): ReadingOutcome =
        when (val marker = subject.marker) {
            null -> ReadingOutcome.unknown(subject.noMarkerReason)
            is PromotionMarker -> aggregate(
                outages = promotionSamples.outages(marker.levels, window),
                inFlight = InFlight.of(promotionSamples.leadTimes(marker.levels, window), window),
            )

            is EnvironmentMarker -> aggregate(
                outages = environmentSamples.outages(marker.slot, window),
                inFlight = null,
            )
        }

    companion object {
        /**
         * Median of the times to restore in the value, p90, mean, min, max and count in the details.
         *
         * Details also carry the outages still going on at the end of the window (`open`, and the
         * start of the oldest one, `openSince`) and how the builds in flight were left out of them
         * (`inFlight`).
         *
         * @param inFlight Builds in flight, `null` when nothing is left out as in flight — the
         * details then say nothing about them
         */
        fun aggregate(outages: List<OutageSample>, inFlight: InFlight?): ReadingOutcome {
            val open = outages.filter { it.open }
            val openFailures = if (inFlight == null) open else open.filterNot { it.start in inFlight }
            val openDetails = buildMap {
                put("open", openFailures.size)
                put("openSince", openFailures.minOfOrNull { it.start })
                if (inFlight != null) {
                    put("inFlight", inFlight.details(excluded = open.size - openFailures.size))
                }
            }
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
