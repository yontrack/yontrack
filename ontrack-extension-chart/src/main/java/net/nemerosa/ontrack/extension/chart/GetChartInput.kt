package net.nemerosa.ontrack.extension.chart

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.graphql.support.TypeRef

@APIDescription("Input to get some chart data")
data class GetChartInput(
    @APIDescription("Name of the chart")
    val name: String,
    @APIDescription("General options for the chart")
    @TypeRef(embedded = true)
    val options: GetChartOptions,
    @APIDescription("Parameters for the chart (as JSON)")
    val parameters: JsonNode,
)
