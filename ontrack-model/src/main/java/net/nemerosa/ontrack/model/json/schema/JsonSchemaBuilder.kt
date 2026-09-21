package net.nemerosa.ontrack.model.json.schema

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.hasDefaultValue
import net.nemerosa.ontrack.model.annotations.APIIgnore
import net.nemerosa.ontrack.model.annotations.APIOptional
import net.nemerosa.ontrack.model.annotations.getPropertyDescription
import net.nemerosa.ontrack.model.annotations.getPropertyName
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component
import java.time.Duration
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.*
import kotlin.reflect.jvm.jvmName

@Component
class JsonSchemaBuilderService(
    private val applicationContext: ApplicationContext,
) : JsonTypeBuilder {

    fun createSchema(
        ref: String,
        id: String,
        title: String,
        description: String,
        root: KClass<*>
    ): JsonSchema {
        val defs = mutableMapOf<String, JsonType>()
        val builder = createSchemaBuilder(defs)
        val rootType = builder.toRoot(root, description)
        return JsonSchema(
            ref = ref,
            id = id,
            title = title,
            description = description,
            defs = defs,
            root = rootType,
        )
    }

    fun createSchema(
        ref: String,
        id: String,
        title: String,
        description: String,
        root: JsonTypeProvider,
    ): JsonSchema {
        val defs = mutableMapOf<String, JsonType>()
        val builder = createSchemaBuilder(defs)
        val rootType = root.jsonType(builder)
        return if (rootType is JsonObjectType) {
            JsonSchema(
                ref = ref,
                id = id,
                title = title,
                description = description,
                defs = defs,
                root = rootType,
            )
        } else {
            error("Root type must be an object")
        }
    }

    override fun toType(type: KType, description: String?): JsonType {
        val defs = mutableMapOf<String, JsonType>()
        val builder = createSchemaBuilder(defs)
        return builder.toType(type, description)
    }

    private fun createSchemaBuilder(defs: MutableMap<String, JsonType>) =
        JsonSchemaBuilder(
            clsGetter = { cls ->
                applicationContext.getBeansOfType(cls.java).values.single()
            },
            defs = defs,
        )
}

private data class JsonTypeContext(
    val paths: List<String> = emptyList(),
) {
    fun addProperty(propertyName: String, propertyReturnType: KType): JsonTypeContext {
        return JsonTypeContext(
            paths = paths + propertyName + propertyReturnType.toString(),
        )
    }

    override fun toString(): String = paths.joinToString(" > ")

    companion object {
        fun ofType(type: KType) = JsonTypeContext(
            paths = listOf(type.toString())
        )
    }
}

private class JsonSchemaBuilder(
    private val clsGetter: (cls: KClass<out Any>) -> Any,
    private val defs: MutableMap<String, JsonType>,
) : JsonTypeBuilder {

    fun toRoot(type: KClass<*>, description: String? = null): JsonObjectType =
        toObject(type.starProjectedType, description, JsonTypeContext.ofType(type.starProjectedType))

    override fun toType(
        type: KType,
        description: String?
    ): JsonType =
        toType(type, description, JsonTypeContext.ofType(type))

    private fun toType(type: KType, description: String?, context: JsonTypeContext): JsonType {
        val cls = type.classifier as KClass<*>
        return when {
            cls == String::class -> JsonStringType(description)
            cls == Int::class -> JsonIntType(description)
            cls == Long::class -> JsonLongType(description)
            cls == Boolean::class -> JsonBooleanType(description)
            cls == List::class -> toList(type, description, context)
            cls == Map::class -> toMap(type, description, context)
            cls == JsonNode::class -> JsonRawJsonType(description)
            cls == Duration::class -> JsonDurationType(description)
            cls == Double::class -> JsonDoubleType(description)
            cls.isSubclassOf(Enum::class) -> toEnumType(cls, description)
            cls.jvmName.startsWith("net.nemerosa.ontrack.") -> toObject(type, description, context)
            else -> error("$type is not supported at $context")
        }
    }

    private fun toEnumType(cls: KClass<*>, description: String?) = JsonEnumType(
        values = cls.java.enumConstants.map { it.toString() },
        description = description,
    )

    private fun toMap(type: KType, description: String? = null, context: JsonTypeContext): JsonMapObjectType {
        val arguments = type.arguments
        if (arguments.size != 2) {
            error("Map must have exactly two type arguments: $context")
        }

        val keyType = arguments[0]
        if (keyType.type?.classifier != String::class) {
            error("Map key must be a string: $context")
        }

        val itemType = arguments[1].type
            ?.run { toType(this, description, context) }
            ?: error("Map must be typed")

        return JsonMapObjectType(
            itemType = itemType,
            description = description,
        )
    }

    private fun toList(type: KType, description: String? = null, context: JsonTypeContext): JsonArrayType {
        val itemType = type.arguments.firstOrNull()?.type
            ?.run { toType(this, description, context) }
            ?: error("List must be typed")
        return JsonArrayType(itemType, description)
    }

    private fun toObject(type: KType, description: String? = null, context: JsonTypeContext): JsonObjectType {
        val cls = type.classifier as KClass<*>

        val excludedProperties = mutableSetOf<String>()
        val dynamicJsonSchema = cls.findAnnotation<DynamicJsonSchema>()
        if (dynamicJsonSchema != null) {
            excludedProperties += dynamicJsonSchema.discriminatorProperty
            excludedProperties += dynamicJsonSchema.configurationProperty
        }

        val oProperties = mutableMapOf<String, JsonType>()
        val oRequired = mutableListOf<String>()
        val properties = cls.memberProperties
        for (property in properties) {
            if (property.name !in excludedProperties && !property.hasAnnotation<APIIgnore>()) {
                val propertyName = getPropertyName(property)
                val propertyReturnType = property.returnType
                val schemaRef = property.findAnnotation<JsonSchemaRef>()
                val jsonSchemaPropertiesContributor = property.findAnnotation<JsonSchemaPropertiesContributor>()
                val jsonSchemaType = property.findAnnotation<JsonSchemaType>()
                val jsonSchemaMapValueType = property.findAnnotation<JsonSchemaMapValueType>()
                val jsonSchemaString = property.findAnnotation<JsonSchemaString>()
                val jsonSchemaIgnore = property.findAnnotation<JsonSchemaIgnore>()
                if (jsonSchemaIgnore != null) {
                    continue
                } else if (jsonSchemaString != null) {
                    oProperties[propertyName] = JsonStringType(
                        description = getPropertyDescription(property),
                    )
                } else if (schemaRef != null) {
                    oProperties[propertyName] = JsonRefType(
                        ref = schemaRef.value,
                        description = getPropertyDescription(property),
                    )
                } else if (jsonSchemaPropertiesContributor != null) {
                    contributeProperties(oProperties, jsonSchemaPropertiesContributor)
                } else if (jsonSchemaType != null) {
                    val provider = clsGetter(jsonSchemaType.provider) as JsonSchemaTypeProvider
                    oProperties[propertyName] = provider.createType(jsonSchemaType.configuration, this)
                } else if (jsonSchemaMapValueType != null) {
                    contributeTypedMap(
                        oProperties = oProperties,
                        propertyName = propertyName,
                        propertyReturnType = propertyReturnType,
                        jsonSchemaMapValueType = jsonSchemaMapValueType,
                        context = context
                    )
                } else {
                    val propertyType = toType(
                        propertyReturnType,
                        getPropertyDescription(property),
                        context = context.addProperty(propertyName, propertyReturnType),
                    )
                    oProperties[propertyName] = propertyType
                }
                if (!property.returnType.isMarkedNullable && !property.hasDefaultValue(cls) && !property.hasAnnotation<APIOptional>()) {
                    oRequired += propertyName
                }
            }
        }

        val oneOf = dynamicJsonSchema?.let {
            setupDynamicJsonSchema(
                cls = cls,
                dynamicJsonSchema = it,
                oProperties = oProperties,
                oRequired = oRequired
            )
        }

        return JsonObjectType(
            title = cls.java.simpleName,
            description = description,
            properties = oProperties,
            required = oRequired,
            additionalProperties = false,
            oneOf = oneOf,
        )
    }

    private fun contributeTypedMap(
        oProperties: MutableMap<String, JsonType>,
        propertyName: String,
        propertyReturnType: KType,
        jsonSchemaMapValueType: JsonSchemaMapValueType,
        context: JsonTypeContext
    ) {
        val cls = propertyReturnType.classifier as KClass<*>
        if (cls != Map::class) {
            error("JsonSchemaMapValueType can only be used on maps: $context")
        }

        val arguments = propertyReturnType.arguments
        if (arguments.size != 2) {
            error("Map must have exactly two type arguments: $context")
        }

        val keyType = arguments[0]
        if (keyType.type?.classifier != String::class) {
            error("Map key must be a string: $context")
        }

        val itemType = arguments[1].type?.classifier as? KClass<*>?
        if (itemType != JsonNode::class) {
            error("JsonSchemaMapValueType can only be used on Map<String,JsonNode>: $context")
        }

        val typeProvider = clsGetter(jsonSchemaMapValueType.provider)
                as JsonSchemaMapValueTypeProvider

        oProperties += propertyName to JsonMapObjectType(
            description = null,
            itemType = typeProvider.createType(this)
        )
    }

    private fun contributeProperties(
        oProperties: MutableMap<String, JsonType>,
        jsonSchemaPropertiesContributor: JsonSchemaPropertiesContributor
    ) {
        val provider = clsGetter(jsonSchemaPropertiesContributor.provider) as JsonSchemaPropertiesContributorProvider
        oProperties += provider.contributeProperties(
            configuration = jsonSchemaPropertiesContributor.configuration,
            jsonTypeBuilder = this,
        )
    }

    private fun setupDynamicJsonSchema(
        cls: KClass<*>,
        dynamicJsonSchema: DynamicJsonSchema,
        oProperties: MutableMap<String, JsonType>,
        oRequired: MutableList<String>
    ): JsonOneOf {
        // Getting the provider
        val provider = clsGetter(dynamicJsonSchema.provider) as DynamicJsonSchemaProvider

        // Gets the corresponding properties in the holding class
        val clsProperties = cls.memberProperties.associateBy { it.name }
        val discriminatorProperty = clsProperties[dynamicJsonSchema.discriminatorProperty]
            ?: error("Cannot find property ${dynamicJsonSchema.discriminatorProperty} in $cls")
        clsProperties[dynamicJsonSchema.configurationProperty]
            ?: error("Cannot find property ${dynamicJsonSchema.configurationProperty} in $cls")

        // Both properties are required
        if (dynamicJsonSchema.required) {
            oRequired += dynamicJsonSchema.configurationProperty
            oRequired += dynamicJsonSchema.discriminatorProperty
        }

        // Enum property
        val discriminatorValues = provider.discriminatorValues.sorted()
        oProperties[dynamicJsonSchema.discriminatorProperty] = JsonEnumType(
            values = discriminatorValues,
            description = getPropertyDescription(discriminatorProperty),
        )

        // Placeholder for the configuration
        oProperties[dynamicJsonSchema.configurationProperty] = JsonEmptyType.INSTANCE

        // All definitions
        defs += provider.getConfigurationTypes(this).mapKeys { (id, _) ->
            provider.toRef(id)
        }

        // Conditions (oneOf) on the object type
        return JsonOneOf(
            conditions = discriminatorValues.map { id ->
                JsonCondition(
                    constProperty = JsonConstProperty(
                        name = dynamicJsonSchema.discriminatorProperty,
                        value = id,
                    ),
                    refProperty = JsonRefProperty(
                        name = dynamicJsonSchema.configurationProperty,
                        ref = provider.toRef(id)
                    )
                )
            }
        )
    }

}
