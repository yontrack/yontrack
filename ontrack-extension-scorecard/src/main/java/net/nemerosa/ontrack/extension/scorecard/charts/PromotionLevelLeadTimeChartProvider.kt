package net.nemerosa.ontrack.extension.scorecard.charts

import net.nemerosa.ontrack.extension.chart.support.DurationChart
import net.nemerosa.ontrack.extension.chart.support.DurationChartItemData
import net.nemerosa.ontrack.extension.chart.support.Interval
import net.nemerosa.ontrack.extension.scorecard.samples.PromotionSamples
import net.nemerosa.ontrack.model.structure.PromotionLevel
import net.nemerosa.ontrack.model.structure.StructureService
import org.springframework.stereotype.Component

/**
 * Lead time to promotion: from the creation of a build to its first promotion run at the level,
 * the samples of `delivery.leadTime`.
 *
 * A sample is bucketed by the time of this first promotion, the moment the level was reached: this
 * is what makes it part of the window of a reading, so that the chart over a window holds the same
 * samples as the reading over it.
 */
@Component
class PromotionLevelLeadTimeChartProvider(
    structureService: StructureService,
    private val promotionSamples: PromotionSamples,
) : AbstractPromotionLevelChartProvider<DurationChart>(structureService) {

    override val name: String = "promotion-level-lead-time"

    override val title: String = "Lead time to promotion"

    override val type: String = DurationChart.TYPE

    override fun getChart(level: PromotionLevel, interval: Interval, period: String): DurationChart =
        DurationChart.compute(
            items = promotionSamples.leadTimes(listOf(level), interval).map { sample ->
                DurationChartItemData(
                    timestamp = sample.end,
                    value = sample.seconds,
                )
            },
            interval = interval,
            period = period,
        )
}
