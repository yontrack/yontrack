package net.nemerosa.ontrack.model.structure

import tools.jackson.databind.JsonNode

interface ValidationDataTypeAlias {
    val alias: String
    val type: String
    fun parseConfig(data: JsonNode): JsonNode
}