package net.nemerosa.ontrack.extension.dm.charts

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.NullNode
import net.nemerosa.ontrack.extension.chart.ChartDefinition
import net.nemerosa.ontrack.extension.chart.ChartProvider
import net.nemerosa.ontrack.extension.chart.GetChartOptions
import net.nemerosa.ontrack.extension.chart.support.ChartUtils
import net.nemerosa.ontrack.extension.chart.support.PercentageChart
import net.nemerosa.ontrack.extension.chart.support.PercentageChartItemData
import net.nemerosa.ontrack.extension.dm.data.EndToEndPromotionFilter
import net.nemerosa.ontrack.extension.dm.data.EndToEndPromotionsHelper
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.structure.PromotionLevel
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

@Component
class PromotionLevelSuccessRateChartProvider(
    private val endToEndPromotionsHelper: EndToEndPromotionsHelper,
) : ChartProvider<PromotionLevel, PromotionLevelChartParameters, PercentageChart> {

    override val subjectClass: KClass<PromotionLevel> = PromotionLevel::class

    override fun getChartDefinition(subject: PromotionLevel) = ChartDefinition(
        id = name,
        title = "Promotion success rate",
        type = "percentage",
        config = mapOf("name" to "% of success").asJson(),
        parameters = mapOf("id" to subject.id()).asJson()
    )

    override val name: String = "promotion-level-success-rate"

    override fun parseParameters(data: JsonNode): PromotionLevelChartParameters = data.parse()

    override fun getChart(options: GetChartOptions, parameters: PromotionLevelChartParameters): PercentageChart {
        val filter = EndToEndPromotionFilter(
            maxDepth = 1,
            promotionId = parameters.id,
            afterTime = options.actualInterval.start,
            beforeTime = options.actualInterval.end,
        )
        val items = mutableListOf<PercentageChartItemData>()
        endToEndPromotionsHelper.forEachEndToEndPromotionRecord(filter) { record ->
            val buildCreation = record.ref.buildCreation
            val promoted = record.ref.promotionCreation != null
            val value = ChartUtils.percentageFromBoolean(promoted)
            items += PercentageChartItemData(
                timestamp = buildCreation,
                value = value,
            )
        }
        return PercentageChart.compute(
            items,
            interval = options.actualInterval,
            period = options.period,
        )
    }
}