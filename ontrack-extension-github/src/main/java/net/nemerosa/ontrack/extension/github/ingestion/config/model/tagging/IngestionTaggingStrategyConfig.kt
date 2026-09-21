package net.nemerosa.ontrack.extension.github.ingestion.config.model.tagging

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.api.APIDescription

@APIDescription("Configuration for a tagging strategy")
data class IngestionTaggingStrategyConfig(
    @APIDescription("ID of the tagging strategy")
    val type: String,
    @APIDescription("JSON configuration")
    val config: JsonNode?,
)
