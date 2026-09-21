package net.nemerosa.ontrack.extension.github.ingestion.config.parser

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.github.ingestion.config.model.IngestionConfig
import net.nemerosa.ontrack.extension.github.ingestion.config.parser.old.IngestionV1Config
import net.nemerosa.ontrack.json.parse

object ConfigV1Parser : AbstractJsonConfigParser() {
    override fun parse(json: JsonNode): IngestionConfig =
        json.parse<IngestionV1Config>().convert()
}