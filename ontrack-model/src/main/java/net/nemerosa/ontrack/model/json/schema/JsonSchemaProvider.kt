package net.nemerosa.ontrack.model.json.schema

import tools.jackson.databind.JsonNode

/**
 * Component that can provide a JSON schema.
 */
interface JsonSchemaProvider {

    /**
     * Internal key
     */
    val key: String

    /**
     * ID of the schema
     */
    val id: String

    /**
     * Title of the schema
     */
    val title: String

    /**
     * Description of the schema
     */
    val description: String

    /**
     * Creating the schema content
     */
    fun createJsonSchema(): JsonNode

}