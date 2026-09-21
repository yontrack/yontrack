package net.nemerosa.ontrack.model.dashboards

import tools.jackson.databind.JsonNode

data class WidgetInstanceInput(
    val uuid: String?,
    val key: String,
    val config: JsonNode,
    val layout: WidgetLayout,
)