package net.nemerosa.ontrack.service.json.schema

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.model.json.schema.JsonSchemaDefinition
import net.nemerosa.ontrack.model.json.schema.JsonSchemaProvider
import net.nemerosa.ontrack.model.json.schema.JsonSchemaService
import org.springframework.stereotype.Service

@Service
class JsonSchemaServiceImpl(
    private val jsonSchemaProviders: List<JsonSchemaProvider>
) : JsonSchemaService {

    private val jsonSchemaProviderIndex: Map<String, JsonSchemaProvider> by lazy {
        jsonSchemaProviders.associateBy { it.key }
    }

    override val jsonSchemaDefinitions: List<JsonSchemaDefinition>
        get() = jsonSchemaProviders.map {
            JsonSchemaDefinition(
                key = it.key,
                id = it.id,
                title = it.title,
                description = it.description,
            )
        }

    override fun getJsonSchema(key: String): JsonNode =
        jsonSchemaProviderIndex[key]
            ?.createJsonSchema()
            ?: throw JsonSchemaDefinitionNotFoundException(key)

}
