package net.nemerosa.ontrack.extension.chart.core

import tools.jackson.databind.node.NullNode
import net.nemerosa.ontrack.extension.chart.ChartDefinition
import net.nemerosa.ontrack.extension.chart.GetChartOptions
import net.nemerosa.ontrack.extension.chart.support.CountChart
import net.nemerosa.ontrack.extension.chart.support.CountChartItemData
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.structure.StructureService
import net.nemerosa.ontrack.model.structure.ValidationRun
import net.nemerosa.ontrack.model.structure.ValidationStamp
import org.springframework.stereotype.Component

@Component
class ValidationStampFrequencyChartProvider(
    structureService: StructureService,
) : AbstractValidationStampChartProvider<CountChart>(
    structureService,
) {

    override val name: String = "validation-stamp-frequency"

    override fun getChart(runs: List<ValidationRun>, options: GetChartOptions): CountChart =
        CountChart.compute(
            items = runs.map {
                CountChartItemData(
                    timestamp = it.lastStatus.signature.time
                )
            },
            interval = options.actualInterval,
            period = options.period,
        )

    override fun getChartDefinition(subject: ValidationStamp): ChartDefinition? =
        ChartDefinition(
            id = name,
            title = "Validation stamp frequency",
            type = CountChart.TYPE,
            config = NullNode.instance,
            parameters = mapOf(
                "id" to subject.id()
            ).asJson()
        )
}