package net.nemerosa.ontrack.extension.chart.core

import tools.jackson.databind.node.NullNode
import net.nemerosa.ontrack.extension.chart.ChartDefinition
import net.nemerosa.ontrack.extension.chart.GetChartOptions
import net.nemerosa.ontrack.extension.chart.support.DurationChart
import net.nemerosa.ontrack.extension.chart.support.DurationChartItemData
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.structure.RunInfoService
import net.nemerosa.ontrack.model.structure.StructureService
import net.nemerosa.ontrack.model.structure.ValidationRun
import net.nemerosa.ontrack.model.structure.ValidationStamp
import org.springframework.stereotype.Component

@Component
class ValidationStampDurationsChartProvider(
    structureService: StructureService,
    private val runInfoService: RunInfoService,
) : AbstractValidationStampChartProvider<DurationChart>(
    structureService,
) {

    override val name: String = "validation-stamp-durations"

    override fun getChart(runs: List<ValidationRun>, options: GetChartOptions): DurationChart =
        DurationChart.compute(
            items = runs.map { run ->
                val runInfo = runInfoService.getRunInfo(run)
                DurationChartItemData(
                    timestamp = run.lastStatus.signature.time,
                    value = runInfo?.runTime?.toDouble(),
                )
            },
            interval = options.actualInterval,
            period = options.period,
        )

    override fun getChartDefinition(subject: ValidationStamp): ChartDefinition? =
        ChartDefinition(
            id = name,
            title = "Validation stamp duration",
            type = DurationChart.TYPE,
            config = NullNode.instance,
            parameters = mapOf(
                "id" to subject.id()
            ).asJson()
        )
}