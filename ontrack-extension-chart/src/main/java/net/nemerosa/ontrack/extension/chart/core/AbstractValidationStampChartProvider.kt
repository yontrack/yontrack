package net.nemerosa.ontrack.extension.chart.core

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.chart.Chart
import net.nemerosa.ontrack.extension.chart.ChartProvider
import net.nemerosa.ontrack.extension.chart.GetChartOptions
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.StructureService
import net.nemerosa.ontrack.model.structure.ValidationRun
import net.nemerosa.ontrack.model.structure.ValidationStamp
import kotlin.reflect.KClass

abstract class AbstractValidationStampChartProvider<C : Chart>(
    protected val structureService: StructureService,
) : ChartProvider<ValidationStamp, ValidationStampChartParameters, C> {

    override val subjectClass: KClass<ValidationStamp> = ValidationStamp::class

    override fun parseParameters(data: JsonNode): ValidationStampChartParameters = data.parse()

    override fun getChart(options: GetChartOptions, parameters: ValidationStampChartParameters): C {
        val vs = structureService.getValidationStamp(ID.of(parameters.id))
        // Gets the validation runs in this period
        val runs: List<ValidationRun> =
            structureService.getValidationRunsForValidationStampBetweenDates(
                vs.id,
                options.actualInterval.start,
                options.actualInterval.end
            )
        // Gets the chart data
        return getChart(runs, options)
    }

    abstract fun getChart(runs: List<ValidationRun>, options: GetChartOptions): C
}