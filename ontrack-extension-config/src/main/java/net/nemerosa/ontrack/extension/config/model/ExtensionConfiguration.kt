package net.nemerosa.ontrack.extension.config.model

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.api.APIDescription

@APIDescription("Extension configuration")
data class ExtensionConfiguration(
    @APIDescription("ID of the extension")
    val id: String,
    @APIDescription("Configuration data for the extension")
    val data: JsonNode,
)
