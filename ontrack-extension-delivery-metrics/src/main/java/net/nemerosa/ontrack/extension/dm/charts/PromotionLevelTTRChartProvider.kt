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
import net.nemerosa.ontrack.extension.dm.model.EndToEndPromotionRecord
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.structure.PromotionLevel
import org.springframework.stereotype.Component
import java.time.Duration
import kotlin.reflect.KClass

@Component
class PromotionLevelTTRChartProvider(
    private val endToEndPromotionsHelper: EndToEndPromotionsHelper,
) : ChartProvider<PromotionLevel, PromotionLevelChartParameters, DurationChart> {

    override val subjectClass: KClass<PromotionLevel> = PromotionLevel::class

    override fun getChartDefinition(subject: PromotionLevel) = ChartDefinition(
        id = name,
        title = "Promotion time to restore",
        type = "duration",
        config = NullNode.instance,
        parameters = mapOf("id" to subject.id()).asJson()
    )

    override val name: String = "promotion-level-ttr"

    override fun parseParameters(data: JsonNode): PromotionLevelChartParameters = data.parse()

    override fun getChart(options: GetChartOptions, parameters: PromotionLevelChartParameters): DurationChart {
        val filter = EndToEndPromotionFilter(
            maxDepth = 1,
            promotionId = parameters.id,
            afterTime = options.actualInterval.start,
            beforeTime = options.actualInterval.end,
        )
        val items = mutableListOf<DurationChartItemData>()
        var previousRecord: EndToEndPromotionRecord? = null
        endToEndPromotionsHelper.forEachEndToEndPromotionRecord(filter) { record ->
            // If we have a previous reference
            if (previousRecord != null) {
                if (record.target.promotionCreation != null) {
                    // The issues are fixed :) Adding a point
                    // Elapsed time between the creation of the faulty build and its later promotion
                    val elapsedTime = Duration.between(previousRecord!!.ref.buildCreation, record.ref.promotionCreation)
                    val value = elapsedTime.toSeconds().toDouble()
                    items += DurationChartItemData(
                        // Ref. time = time of the promotion
                        timestamp = record.target.promotionCreation,
                        // Elapsed time between the creation of the faulty build and its later promotion
                        value = value,
                    )
                    // ... and remove the previous record
                    previousRecord = null
                } else {
                    // Adding a reference
                    previousRecord = record
                    // ... and going forward with more recent records
                }
            }
            // We don't have a previous reference
            else {
                // Are we on a promotion?
                if (record.target.promotionCreation != null) {
                    // We don't need to record anything, all is good
                } else {
                    // Adding a reference
                    previousRecord = record
                    // ... and going forward with more recent records
                }
            }
        }
        // OK
        return DurationChart.compute(
            items,
            interval = options.actualInterval,
            period = options.period,
        )
    }

}