package net.nemerosa.ontrack.extension.scorecard.computers

import net.nemerosa.ontrack.extension.chart.support.Interval
import net.nemerosa.ontrack.extension.scorecard.engine.*
import net.nemerosa.ontrack.extension.scorecard.model.ReadingKeys
import net.nemerosa.ontrack.extension.scorecard.model.ReadingUnknownReason
import net.nemerosa.ontrack.extension.scorecard.samples.DurationSample
import net.nemerosa.ontrack.extension.scorecard.samples.EnvironmentSamples
import net.nemerosa.ontrack.extension.scorecard.samples.PromotionSamples
import org.springframework.stereotype.Component

/**
 * `delivery.leadTime`: from the creation of a build to the moment it reached the marker.
 *
 * Under a promotion marker, to its first promotion run at the level. Under an environment marker,
 * to the end of its first done deployment in the slot: a redeployment does not reset it. The
 * median, in seconds, goes in the value.
 */
@Component
class LeadTimeReadingComputer(
    private val promotionSamples: PromotionSamples,
    private val environmentSamples: EnvironmentSamples,
) : ReadingComputer {

    override val key: String = ReadingKeys.DELIVERY_LEAD_TIME

    override fun compute(subject: ReadingSubject, window: Interval): ReadingOutcome {
        val samples = when (val marker = subject.marker) {
            null -> return ReadingOutcome.unknown(subject.noMarkerReason)
            is PromotionMarker -> promotionSamples.leadTimes(marker.levels, window)
            is EnvironmentMarker -> environmentSamples.leadTimes(marker.slot, window)
        }
        return aggregate(samples)
    }

    companion object {
        /**
         * Median of the durations in the value, p90, mean, min, max and count in the details.
         */
        fun aggregate(samples: List<DurationSample>): ReadingOutcome {
            val stats = DurationStatistics.of(samples.map { it.seconds })
            return if (stats == null) {
                ReadingOutcome.unknown(ReadingUnknownReason.NO_SAMPLES, mapOf("count" to 0))
            } else {
                ReadingOutcome.measured(stats.median, stats.details)
            }
        }
    }
}
