package net.nemerosa.ontrack.model.json.schema

import com.fasterxml.jackson.annotation.JsonInclude
import tools.jackson.core.JsonGenerator
import tools.jackson.databind.ValueSerializer
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.annotation.JsonSerialize

class JsonObjectType(
    title: String,
    description: String?,
    val properties: Map<String, JsonType>,
    val required: List<String>,
    val additionalProperties: Boolean = false,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val minProperties: Int? = null,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val maxProperties: Int? = null,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val oneOf: JsonOneOf? = null,
) : AbstractJsonNamedType(type = "object", title = title, description = description)

@JsonSerialize(using = JsonOneOfSerializer::class)
class JsonOneOf(
    val conditions: List<JsonCondition>,
)

@JsonSerialize(using = JsonConditionSerializer::class)
class JsonCondition(
    internal val constProperty: JsonConstProperty,
    internal val refProperty: JsonRefProperty,
)

class JsonConstProperty(
    val name: String,
    val value: String,
)

class JsonRefProperty(
    val name: String,
    val ref: String,
)

class JsonOneOfSerializer : ValueSerializer<JsonOneOf>() {
    override fun serialize(value: JsonOneOf, gen: JsonGenerator, serializers: SerializationContext) {
        gen.writeStartArray()
        value.conditions.forEach {
            gen.writeStartObject()
            gen.writePOJOProperty("properties", it)
            gen.writeEndObject()
        }
        gen.writeEndArray()
    }
}

class JsonConditionSerializer : ValueSerializer<JsonCondition>() {
    override fun serialize(condition: JsonCondition, gen: JsonGenerator, serializers: SerializationContext) {
        gen.writeStartObject()
        gen.writePOJOProperty(
            condition.constProperty.name, mapOf(
                "const" to condition.constProperty.value,
            )
        )
        gen.writePOJOProperty(
            condition.refProperty.name, mapOf(
                "\$ref" to "#/\$defs/${condition.refProperty.ref}"
            )
        )
        gen.writeEndObject()
    }
}