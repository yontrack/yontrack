package net.nemerosa.ontrack.extension.chart

import tools.jackson.databind.JsonNode
import kotlin.reflect.KClass

/**
 * Provides a chart
 *
 * @param S Subject for the chart
 * @param T Type for the parameters
 * @param C Type of chart being returned
 */
interface ChartProvider<S : Any, T : Any, C : Chart> {

    /**
     * Subject class
     */
    val subjectClass: KClass<S>

    /**
     * Unique name for this chart
     */
    val name: String

    /**
     * Parsing of the parameters
     */
    fun parseParameters(data: JsonNode): T

    /**
     * Gets the chart for a given input
     */
    fun getChart(options: GetChartOptions, parameters: T): C

    /**
     * Gets the chart definition for the given subject.
     *
     * @return Null if not applicable for this particular instance
     */
    fun getChartDefinition(subject: S): ChartDefinition?

}