package net.nemerosa.ontrack.model.dashboards

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.JsonNode

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class DashboardDefinition(
    val uuid: String? = null,
    val name: String,
    val widgets: List<WidgetDefinition> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class WidgetDefinition(
    val uuid: String? = null,
    val key: String,
    val layout: WidgetLayout,
    val config: JsonNode? = null,
)
