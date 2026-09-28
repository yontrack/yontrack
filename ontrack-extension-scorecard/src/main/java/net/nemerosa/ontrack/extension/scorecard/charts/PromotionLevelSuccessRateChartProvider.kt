package net.nemerosa.ontrack.extension.scorecard.charts

import net.nemerosa.ontrack.extension.chart.support.ChartUtils
import net.nemerosa.ontrack.extension.chart.support.Interval
import net.nemerosa.ontrack.extension.chart.support.PercentageChart
import net.nemerosa.ontrack.extension.chart.support.PercentageChartItemData
import net.nemerosa.ontrack.extension.scorecard.samples.InFlight
import net.nemerosa.ontrack.extension.scorecard.samples.PromotionSamples
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.structure.PromotionLevel
import net.nemerosa.ontrack.model.structure.StructureService
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode

/**
 * Promotion success rate: the share of the builds created in a period which were promoted at the
 * level — the samples of `delivery.successRate`.
 *
 * As in the reading, the builds [in flight][InFlight] at the end of the interval — created within
 * the median lead time of the interval before its end — are left out: they have not had the time
 * to be promoted yet.
 */
@Component
class PromotionLevelSuccessRateChartProvider(
    structureService: StructureService,
    private val promotionSamples: PromotionSamples,
) : AbstractPromotionLevelChartProvider<PercentageChart>(structureService) {

    override val name: String = "promotion-level-success-rate"

    override val title: String = "Promotion success rate"

    override val type: String = "percentage"

    override val config: JsonNode = mapOf("name" to "% of success").asJson()

    override fun getChart(level: PromotionLevel, interval: Interval, period: String): PercentageChart {
        val levels = listOf(level)
        val inFlight = InFlight.of(promotionSamples.leadTimes(levels, interval), interval)
        return PercentageChart.compute(
            items = promotionSamples.builds(levels, interval)
                .filterNot { it.creation in inFlight }
                .map { build ->
                    PercentageChartItemData(
                        timestamp = build.creation,
                        value = ChartUtils.percentageFromBoolean(build.promoted),
                    )
                },
            interval = interval,
            period = period,
        )
    }
}
