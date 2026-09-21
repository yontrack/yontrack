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
import net.nemerosa.ontrack.extension.dm.export.PromotionLevelLeadTimeMetrics
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.structure.PromotionLevel
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

@Component
class E2ELeadTimeChartProvider(
    private val endToEndPromotionsHelper: EndToEndPromotionsHelper,
    private val promotionLevelLeadTimeMetrics: PromotionLevelLeadTimeMetrics,
) : ChartProvider<PromotionLevel, E2EChartParameters, DurationChart> {

    override val subjectClass: KClass<PromotionLevel> = PromotionLevel::class

    override fun getChartDefinition(subject: PromotionLevel) = ChartDefinition(
        id = name,
        title = "E2E Lead time from promotion",
        type = "duration",
        config = NullNode.instance,
        parameters = E2EChartParameters(
            refPromotionId = subject.id(),
            samePromotion = true,
            targetPromotionId = null,
            targetProject = subject.project.name,
            maxDepth = 5,
        ).asJson(),
    )

    override val name: String = "e2e-lead-time"

    override fun parseParameters(data: JsonNode): E2EChartParameters = data.parse()

    override fun getChart(options: GetChartOptions, parameters: E2EChartParameters): DurationChart {
        val filter = EndToEndPromotionFilter(
            maxDepth = parameters.maxDepth,
            promotionId = parameters.refPromotionId,
            samePromotion = parameters.samePromotion,
            targetPromotionId = parameters.targetPromotionId,
            targetProject = parameters.targetProject,
            afterTime = options.actualInterval.start,
            beforeTime = options.actualInterval.end,
        )
        val items = mutableListOf<DurationChartItemData>()
        endToEndPromotionsHelper.forEachEndToEndPromotionRecord(filter) { record ->
            promotionLevelLeadTimeMetrics.recordTime(record)?.let { (timestamp, duration) ->
                items += DurationChartItemData(
                    timestamp = timestamp,
                    value = duration.toSeconds().toDouble(),
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