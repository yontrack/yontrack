package net.nemerosa.ontrack.extension.github.ingestion.config.parser

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.github.ingestion.config.model.IngestionConfig

interface JsonConfigParser {

    fun parse(json: JsonNode): IngestionConfig

}