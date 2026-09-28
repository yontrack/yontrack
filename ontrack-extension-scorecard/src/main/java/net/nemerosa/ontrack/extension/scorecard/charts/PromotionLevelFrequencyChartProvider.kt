package net.nemerosa.ontrack.extension.scorecard.charts

import net.nemerosa.ontrack.extension.chart.support.CountChart
import net.nemerosa.ontrack.extension.chart.support.CountChartItemData
import net.nemerosa.ontrack.extension.chart.support.Interval
import net.nemerosa.ontrack.extension.scorecard.samples.PromotionSamples
import net.nemerosa.ontrack.model.structure.PromotionLevel
import net.nemerosa.ontrack.model.structure.StructureService
import org.springframework.stereotype.Component

/**
 * Promotion frequency: every promotion run at the level, counted by period — the samples of
 * `delivery.frequency`.
 */
@Component
class PromotionLevelFrequencyChartProvider(
    structureService: StructureService,
    private val promotionSamples: PromotionSamples,
) : AbstractPromotionLevelChartProvider<CountChart>(structureService) {

    override val name: String = "promotion-level-frequency"

    override val title: String = "Promotion frequency"

    override val type: String = CountChart.TYPE

    override fun getChart(level: PromotionLevel, interval: Interval, period: String): CountChart =
        CountChart.compute(
            items = promotionSamples.promotions(listOf(level), interval).map { sample ->
                CountChartItemData(timestamp = sample.time)
            },
            interval = interval,
            period = period,
        )
}
