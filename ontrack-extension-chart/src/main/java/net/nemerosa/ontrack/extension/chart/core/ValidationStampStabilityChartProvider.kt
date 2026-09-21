package net.nemerosa.ontrack.extension.chart.core

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.NullNode
import net.nemerosa.ontrack.extension.chart.ChartDefinition
import net.nemerosa.ontrack.extension.chart.GetChartOptions
import net.nemerosa.ontrack.extension.chart.support.*
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.structure.StructureService
import net.nemerosa.ontrack.model.structure.ValidationRun
import net.nemerosa.ontrack.model.structure.ValidationStamp
import org.springframework.stereotype.Component

@Component
class ValidationStampStabilityChartProvider(
    structureService: StructureService,
) : AbstractValidationStampChartProvider<PercentageChart>(
    structureService,
) {

    override val name: String = "validation-stamp-stability"

    override fun getChart(runs: List<ValidationRun>, options: GetChartOptions): PercentageChart =
        PercentageChart.compute(
            items = runs.map { run ->
                PercentageChartItemData(
                    timestamp = run.lastStatus.signature.time,
                    value = ChartUtils.percentageFromBoolean(run.lastStatus.isPassed)
                )
            },
            interval = options.actualInterval,
            period = options.period,
        )

    override fun getChartDefinition(subject: ValidationStamp): ChartDefinition? =
        ChartDefinition(
            id = name,
            title = "Validation stamp stability",
            type = "percentage",
            config = mapOf(
                "name" to "% of success"
            ).asJson(),
            parameters = mapOf(
                "id" to subject.id()
            ).asJson()
        )
}