package net.nemerosa.ontrack.extension.scorecard.computers

import net.nemerosa.ontrack.extension.chart.support.Interval
import net.nemerosa.ontrack.extension.scorecard.engine.PromotionMarker
import net.nemerosa.ontrack.extension.scorecard.engine.ReadingComputer
import net.nemerosa.ontrack.extension.scorecard.engine.ReadingOutcome
import net.nemerosa.ontrack.extension.scorecard.engine.ReadingSubject
import net.nemerosa.ontrack.extension.scorecard.model.ReadingKeys
import net.nemerosa.ontrack.extension.scorecard.model.ReadingUnknownReason
import net.nemerosa.ontrack.extension.scorecard.samples.BuildSample
import net.nemerosa.ontrack.extension.scorecard.samples.InFlight
import net.nemerosa.ontrack.extension.scorecard.samples.PromotionSamples
import org.springframework.stereotype.Component

/**
 * `delivery.successRate`: the share of the builds which reached the marker, as a percentage.
 *
 * Under a promotion marker, the builds created in the window on the branches of the marker, and
 * promoted at the level of their branch. The builds [in flight][InFlight] — created within the
 * median lead time of the window before its end — are left out, and the details say how.
 */
@Component
class SuccessRateReadingComputer(
    private val promotionSamples: PromotionSamples,
) : ReadingComputer {

    override val key: String = ReadingKeys.DELIVERY_SUCCESS_RATE

    override fun compute(subject: ReadingSubject, window: Interval): ReadingOutcome =
        when (val marker = subject.marker) {
            null -> ReadingOutcome.unknown(ReadingUnknownReason.NO_MARKER)
            is PromotionMarker -> aggregate(
                builds = promotionSamples.builds(marker.levels, window),
                inFlight = InFlight.of(promotionSamples.leadTimes(marker.levels, window), window),
            )
        }

    companion object {
        /**
         * Percentage of the builds which were promoted, the builds in flight left out.
         *
         * Details: `count` (builds counted), `promoted` (those promoted) and `inFlight`
         * (`leadTime`, `since` and the number of builds `excluded`).
         */
        fun aggregate(builds: List<BuildSample>, inFlight: InFlight): ReadingOutcome {
            val counted = builds.filterNot { it.creation in inFlight }
            val promoted = counted.count { it.promoted }
            val details = mapOf(
                "count" to counted.size,
                "promoted" to promoted,
                "inFlight" to inFlight.details(excluded = builds.size - counted.size),
            )
            return if (counted.isEmpty()) {
                ReadingOutcome.unknown(ReadingUnknownReason.NO_SAMPLES, details)
            } else {
                ReadingOutcome.measured(100.0 * promoted / counted.size, details)
            }
        }
    }
}
