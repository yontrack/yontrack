package net.nemerosa.ontrack.extension.config.ci.model

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.api.APIDescription

@APIDescription("Condition configuration")
data class CIConditionConfig(
    @APIDescription("Identifier of the condition")
    val name: String,
    @APIDescription("Configuration for the condition")
    val config: JsonNode,
)
