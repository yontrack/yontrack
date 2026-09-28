package net.nemerosa.ontrack.extension.scorecard.charts

import net.nemerosa.ontrack.extension.chart.support.DurationChart
import net.nemerosa.ontrack.extension.chart.support.DurationChartItemData
import net.nemerosa.ontrack.extension.chart.support.Interval
import net.nemerosa.ontrack.extension.scorecard.samples.Outages
import net.nemerosa.ontrack.extension.scorecard.samples.PromotionSamples
import net.nemerosa.ontrack.model.structure.PromotionLevel
import net.nemerosa.ontrack.model.structure.StructureService
import org.springframework.stereotype.Component

/**
 * Promotion time to restore: the [outages][Outages] of the branch of the level, from the **first**
 * unpromoted build after a promoted one to the next promotion — the samples of `delivery.mttr`.
 *
 * An outage is bucketed by its restoration. The outages still going on have no time to restore yet.
 */
@Component
class PromotionLevelTTRChartProvider(
    structureService: StructureService,
    private val promotionSamples: PromotionSamples,
) : AbstractPromotionLevelChartProvider<DurationChart>(structureService) {

    override val name: String = "promotion-level-ttr"

    override val title: String = "Promotion time to restore"

    override val type: String = DurationChart.TYPE

    override fun getChart(level: PromotionLevel, interval: Interval, period: String): DurationChart =
        DurationChart.compute(
            items = promotionSamples.outages(listOf(level), interval)
                .mapNotNull { it.timeToRestore }
                .map { sample ->
                    DurationChartItemData(
                        timestamp = sample.end,
                        value = sample.seconds,
                    )
                },
            interval = interval,
            period = period,
        )
}
