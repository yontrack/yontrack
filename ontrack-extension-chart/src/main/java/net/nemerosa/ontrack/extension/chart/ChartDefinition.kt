package net.nemerosa.ontrack.extension.chart

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.api.APIDescription

/**
 * Reference to a chart that an entity exposes.
 *
 * @property id Unique ID for this chart in the context of the entity exposing the charts
 * @property title Chart title
 * @property type Chart type, as supported by the client: `count`, `percentage`, `duration`, etc.
 * @property config Additional fields that the chart [type] required
 * @property parameters Parameters that the client must pass to get the chart data
 */
@APIDescription("Reference to a chart that an entity exposes.")
data class ChartDefinition(
    @APIDescription("Unique ID for this chart in the context of the entity exposing the charts")
    val id: String,
    @APIDescription("Chart title")
    val title: String,
    @APIDescription("Chart type, as supported by the client: `count`, `percentage`, `duration`, etc.")
    val type: String,
    @APIDescription("Additional fields that the chart type required")
    val config: JsonNode,
    @APIDescription("Parameters that the client must pass to get the chart data")
    val parameters: JsonNode,
)
