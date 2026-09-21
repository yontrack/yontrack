package net.nemerosa.ontrack.extension.github.ingestion.config.parser

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.github.ingestion.config.model.IngestionConfig
import net.nemerosa.ontrack.json.parse

object ConfigV2Parser : AbstractJsonConfigParser() {
    override fun parse(json: JsonNode): IngestionConfig =
        json.parse()
}