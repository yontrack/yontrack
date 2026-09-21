package net.nemerosa.ontrack.extension.config.model

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.api.APIDescription

@APIDescription("Configuration for a validation stamp data")
data class ValidationStampDataConfiguration(
    @APIDescription("FQCN of the validation data type")
    val type: String,
    @APIDescription("Configuration data for the validation data type")
    val data: JsonNode,
)
