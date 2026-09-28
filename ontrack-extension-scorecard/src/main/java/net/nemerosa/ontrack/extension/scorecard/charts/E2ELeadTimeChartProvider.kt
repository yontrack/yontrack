package net.nemerosa.ontrack.extension.scorecard.charts

import net.nemerosa.ontrack.extension.chart.ChartDefinition
import net.nemerosa.ontrack.extension.chart.ChartProvider
import net.nemerosa.ontrack.extension.chart.GetChartOptions
import net.nemerosa.ontrack.extension.chart.support.DurationChart
import net.nemerosa.ontrack.extension.chart.support.DurationChartItemData
import net.nemerosa.ontrack.extension.scorecard.charts.e2e.EndToEndPromotionFilter
import net.nemerosa.ontrack.extension.scorecard.charts.e2e.EndToEndPromotionRecord
import net.nemerosa.ontrack.extension.scorecard.charts.e2e.EndToEndPromotionsHelper
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.structure.PromotionLevel
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.NullNode
import java.time.Duration
import java.time.LocalDateTime
import kotlin.reflect.KClass

/**
 * End-to-end lead time: from the creation of an upstream build to the promotion of a downstream
 * build, across the build links.
 *
 * It is not a reading, since it is not about one project: it keeps its own query.
 */
@Component
class E2ELeadTimeChartProvider(
    private val endToEndPromotionsHelper: EndToEndPromotionsHelper,
) : ChartProvider<PromotionLevel, E2EChartParameters, DurationChart> {

    override val subjectClass: KClass<PromotionLevel> = PromotionLevel::class

    override fun getChartDefinition(subject: PromotionLevel) = ChartDefinition(
        id = name,
        title = "E2E Lead time from promotion",
        type = DurationChart.TYPE,
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
            leadTime(record)?.let { (timestamp, duration) ->
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

    companion object {

        /**
         * Lead time of a record: from the creation of the upstream build to the latest of the two
         * promotions, timestamped by the creation of the upstream build.
         *
         * @return `null` if one of the two builds is not promoted
         */
        fun leadTime(record: EndToEndPromotionRecord): Pair<LocalDateTime, Duration>? {
            val refPromotionCreation = record.ref.promotionCreation
            val targetPromotionCreation = record.target.promotionCreation
            return if (record.ref.promotion != null && refPromotionCreation != null && targetPromotionCreation != null) {
                val maxPromotionTime = maxOf(refPromotionCreation, targetPromotionCreation)
                record.ref.buildCreation to Duration.between(record.ref.buildCreation, maxPromotionTime)
            } else {
                null
            }
        }
    }

}
