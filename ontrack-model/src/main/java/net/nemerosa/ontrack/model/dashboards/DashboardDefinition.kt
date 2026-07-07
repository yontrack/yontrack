package net.nemerosa.ontrack.model.dashboards

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.NullNode

@JsonIgnoreProperties(ignoreUnknown = true)
data class DashboardDefinition(
    val uuid: String? = null,
    val name: String,
    val widgets: List<WidgetDefinition> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class WidgetDefinition(
    val uuid: String? = null,
    val key: String,
    val config: JsonNode = NullNode.instance,
    val layout: WidgetLayout,
)
