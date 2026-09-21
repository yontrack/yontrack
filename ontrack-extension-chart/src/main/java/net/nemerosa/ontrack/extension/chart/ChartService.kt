package net.nemerosa.ontrack.extension.chart

import tools.jackson.databind.JsonNode

interface ChartService {

    /**
     * Given a chart request, returns some chart data.
     */
    fun getChart(input: GetChartInput): JsonNode

}