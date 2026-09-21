package net.nemerosa.ontrack.model.json.schema

import tools.jackson.databind.JsonNode

interface JsonSchemaService {

    val jsonSchemaDefinitions: List<JsonSchemaDefinition>

    fun getJsonSchema(key: String): JsonNode

}