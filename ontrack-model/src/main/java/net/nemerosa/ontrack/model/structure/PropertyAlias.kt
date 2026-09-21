package net.nemerosa.ontrack.model.structure

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder

interface PropertyAlias {
    val alias: String
    val type: String
    fun parseConfig(data: JsonNode): JsonNode
    fun createJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType
}