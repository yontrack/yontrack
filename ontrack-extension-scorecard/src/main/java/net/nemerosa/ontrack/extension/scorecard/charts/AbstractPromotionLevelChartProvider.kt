package net.nemerosa.ontrack.extension.scorecard.charts

import net.nemerosa.ontrack.extension.chart.Chart
import net.nemerosa.ontrack.extension.chart.ChartDefinition
import net.nemerosa.ontrack.extension.chart.ChartProvider
import net.nemerosa.ontrack.extension.chart.GetChartOptions
import net.nemerosa.ontrack.extension.chart.support.Interval
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.PromotionLevel
import net.nemerosa.ontrack.model.structure.StructureService
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.NullNode
import kotlin.reflect.KClass

/**
 * Chart of a promotion level, read from the [sample functions][net.nemerosa.ontrack.extension.scorecard.samples.PromotionSamples]
 * of the readings, with the level as marker and its branch as scope: a chart and the reading
 * beside it are computed from the same samples. The chart buckets them by period.
 */
abstract class AbstractPromotionLevelChartProvider<C : Chart>(
    private val structureService: StructureService,
) : ChartProvider<PromotionLevel, PromotionLevelChartParameters, C> {

    /**
     * Title of the chart
     */
    protected abstract val title: String

    /**
     * Type of the chart
     */
    protected abstract val type: String

    /**
     * Configuration of the chart
     */
    protected open val config: JsonNode = NullNode.instance

    /**
     * Gets the chart of the level over the interval, bucketed by period.
     */
    protected abstract fun getChart(level: PromotionLevel, interval: Interval, period: String): C

    override val subjectClass: KClass<PromotionLevel> = PromotionLevel::class

    override fun getChartDefinition(subject: PromotionLevel) = ChartDefinition(
        id = name,
        title = title,
        type = type,
        config = config,
        parameters = mapOf("id" to subject.id()).asJson()
    )

    override fun parseParameters(data: JsonNode): PromotionLevelChartParameters = data.parse()

    override fun getChart(options: GetChartOptions, parameters: PromotionLevelChartParameters): C =
        getChart(
            level = structureService.getPromotionLevel(ID.of(parameters.id)),
            interval = options.actualInterval,
            period = options.period,
        )
}
