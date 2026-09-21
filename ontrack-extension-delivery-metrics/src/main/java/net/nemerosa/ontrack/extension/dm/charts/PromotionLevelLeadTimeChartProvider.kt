package net.nemerosa.ontrack.extension.dm.charts

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.NullNode
import net.nemerosa.ontrack.extension.chart.ChartDefinition
import net.nemerosa.ontrack.extension.chart.ChartProvider
import net.nemerosa.ontrack.extension.chart.GetChartOptions
import net.nemerosa.ontrack.extension.chart.support.DurationChart
import net.nemerosa.ontrack.extension.chart.support.DurationChartItemData
import net.nemerosa.ontrack.extension.dm.data.EndToEndPromotionFilter
import net.nemerosa.ontrack.extension.dm.data.EndToEndPromotionsHelper
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.structure.PromotionLevel
import org.springframework.stereotype.Component
import java.time.Duration
import kotlin.reflect.KClass

@Component
class PromotionLevelLeadTimeChartProvider(
    private val endToEndPromotionsHelper: EndToEndPromotionsHelper,
) : ChartProvider<PromotionLevel, PromotionLevelChartParameters, DurationChart> {

    override val subjectClass: KClass<PromotionLevel> = PromotionLevel::class

    override fun getChartDefinition(subject: PromotionLevel) = ChartDefinition(
        id = name,
        title = "Lead time to promotion",
        type = "duration",
        config = NullNode.instance,
        parameters = mapOf("id" to subject.id()).asJson()
    )

    override val name: String = "promotion-level-lead-time"

    override fun parseParameters(data: JsonNode): PromotionLevelChartParameters = data.parse()

    override fun getChart(options: GetChartOptions, parameters: PromotionLevelChartParameters): DurationChart {
        val filter = EndToEndPromotionFilter(
            maxDepth = 1,
            promotionId = parameters.id,
            afterTime = options.actualInterval.start,
            beforeTime = options.actualInterval.end,
        )
        val items = mutableListOf<DurationChartItemData>()
        endToEndPromotionsHelper.forEachEndToEndPromotionRecord(filter) { record ->
            val buildCreation = record.ref.buildCreation
            val promotionCreation = record.ref.promotionCreation
            if (promotionCreation != null) {
                val duration = Duration.between(buildCreation, promotionCreation).toSeconds()
                items += DurationChartItemData(
                    timestamp = buildCreation,
                    value = duration.toDouble(),
                )
            }
        }
        return DurationChart.compute(
            items,
            interval = options.actualInterval,
            period = options.period,
        )
    }

}