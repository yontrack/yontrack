package net.nemerosa.ontrack.extension.dm.charts

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.NullNode
import net.nemerosa.ontrack.extension.chart.ChartDefinition
import net.nemerosa.ontrack.extension.chart.ChartProvider
import net.nemerosa.ontrack.extension.chart.GetChartOptions
import net.nemerosa.ontrack.extension.chart.support.CountChart
import net.nemerosa.ontrack.extension.chart.support.CountChartItemData
import net.nemerosa.ontrack.extension.dm.data.EndToEndPromotionFilter
import net.nemerosa.ontrack.extension.dm.data.EndToEndPromotionsHelper
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.structure.PromotionLevel
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

@Component
class PromotionLevelFrequencyChartProvider(
    private val endToEndPromotionsHelper: EndToEndPromotionsHelper,
) : ChartProvider<PromotionLevel, PromotionLevelChartParameters, CountChart> {

    override val subjectClass: KClass<PromotionLevel> = PromotionLevel::class

    override fun getChartDefinition(subject: PromotionLevel) = ChartDefinition(
        id = name,
        title = "Promotion frequency",
        type = "count",
        config = NullNode.instance,
        parameters = mapOf("id" to subject.id()).asJson()
    )

    override val name: String = "promotion-level-frequency"

    override fun parseParameters(data: JsonNode): PromotionLevelChartParameters = data.parse()

    override fun getChart(options: GetChartOptions, parameters: PromotionLevelChartParameters): CountChart {
        val filter = EndToEndPromotionFilter(
            maxDepth = 1,
            promotionId = parameters.id,
            afterTime = options.actualInterval.start,
            beforeTime = options.actualInterval.end,
        )
        val items = mutableListOf<CountChartItemData>()
        endToEndPromotionsHelper.forEachEndToEndPromotionRecord(filter) { record ->
            val promotionCreation = record.ref.promotionCreation
            if (promotionCreation != null) {
                items += CountChartItemData(
                    timestamp = promotionCreation
                )
            }
        }
        return CountChart.compute(
            items,
            interval = options.actualInterval,
            period = options.period,
        )
    }

}