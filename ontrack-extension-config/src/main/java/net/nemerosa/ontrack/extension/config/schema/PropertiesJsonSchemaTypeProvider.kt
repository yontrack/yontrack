package net.nemerosa.ontrack.extension.config.schema

import net.nemerosa.ontrack.model.json.schema.*
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.structure.PropertyAlias
import net.nemerosa.ontrack.model.structure.PropertyService
import org.springframework.stereotype.Component
import kotlin.reflect.full.hasAnnotation

@Component
class PropertiesJsonSchemaTypeProvider(
    private val propertyService: PropertyService,
    private val propertyAliases: List<PropertyAlias>,
) : JsonSchemaTypeProvider {

    override fun createType(configuration: String, jsonTypeBuilder: JsonTypeBuilder): JsonType {
        val entityType = ProjectEntityType.valueOf(configuration)

        // One property per type
        val properties = mutableMapOf<String, JsonType>()
        propertyService.propertyTypes.forEach { propertyType ->
            val ignore = propertyType::class.hasAnnotation<JsonSchemaIgnore>()
            if (!ignore && propertyType.supportedEntityTypes.contains(entityType)) {
                val typeName = propertyType.typeName
                val jsonType = propertyType.createConfigJsonType(jsonTypeBuilder)
                properties[typeName] = jsonType
                // Alias?
                propertyAliases.forEach { alias ->
                    if (alias.type == typeName) {
                        properties[alias.alias] = alias.createJsonType(jsonTypeBuilder)
                    }
                }
            }
        }

        return JsonObjectType(
            title = "PropertyConfiguration",
            description = "Property configuration",
            properties = properties,
            required = emptyList(),
            additionalProperties = false,
            oneOf = null,
        )
    }

}