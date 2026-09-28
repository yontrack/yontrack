package net.nemerosa.ontrack.extension.scorecard.computers

import net.nemerosa.ontrack.extension.chart.support.Interval
import net.nemerosa.ontrack.extension.scorecard.engine.EnvironmentMarker
import net.nemerosa.ontrack.extension.scorecard.engine.PromotionMarker
import net.nemerosa.ontrack.extension.scorecard.engine.ReadingComputer
import net.nemerosa.ontrack.extension.scorecard.engine.ReadingOutcome
import net.nemerosa.ontrack.extension.scorecard.engine.ReadingSubject
import net.nemerosa.ontrack.extension.scorecard.model.ReadingKeys
import net.nemerosa.ontrack.extension.scorecard.model.ReadingUnknownReason
import net.nemerosa.ontrack.extension.scorecard.samples.EnvironmentSamples
import net.nemerosa.ontrack.extension.scorecard.samples.EventSample
import net.nemerosa.ontrack.extension.scorecard.samples.PromotionSamples
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * `delivery.frequency`: how often the marker is reached, per week.
 *
 * Under a promotion marker, the promotion runs at the level. Under an environment marker, the
 * deployments done in the slot. The raw count goes in the details.
 */
@Component
class FrequencyReadingComputer(
    private val promotionSamples: PromotionSamples,
    private val environmentSamples: EnvironmentSamples,
) : ReadingComputer {

    override val key: String = ReadingKeys.DELIVERY_FREQUENCY

    override fun compute(subject: ReadingSubject, window: Interval): ReadingOutcome {
        val samples = when (val marker = subject.marker) {
            null -> return ReadingOutcome.unknown(subject.noMarkerReason)
            is PromotionMarker -> promotionSamples.promotions(marker.levels, window)
            is EnvironmentMarker -> environmentSamples.deployments(marker.slot, window)
        }
        return aggregate(samples, window)
    }

    companion object {

        private val WEEK_SECONDS = Duration.ofDays(7).toSeconds().toDouble()

        /**
         * Number of events normalised per week of the window, the raw count in the details.
         */
        fun aggregate(samples: List<EventSample>, window: Interval): ReadingOutcome {
            val count = samples.size
            return if (count == 0) {
                ReadingOutcome.unknown(ReadingUnknownReason.NO_SAMPLES, mapOf("count" to 0))
            } else {
                val weeks = Duration.between(window.start, window.end).toSeconds() / WEEK_SECONDS
                ReadingOutcome.measured(count / weeks, mapOf("count" to count))
            }
        }
    }
}
