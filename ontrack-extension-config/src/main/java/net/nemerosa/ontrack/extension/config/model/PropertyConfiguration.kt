package net.nemerosa.ontrack.extension.config.model

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.api.APIDescription

@APIDescription("Property configuration")
data class PropertyConfiguration(
    @APIDescription("FQCN of the property type")
    val type: String,
    @APIDescription("Configuration data for the property")
    val data: JsonNode,
)
