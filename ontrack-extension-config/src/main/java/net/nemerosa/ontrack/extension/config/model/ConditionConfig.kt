package net.nemerosa.ontrack.extension.config.model

import tools.jackson.databind.JsonNode

data class ConditionConfig(
    val name: String,
    val config: JsonNode,
)
