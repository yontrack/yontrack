package net.nemerosa.ontrack.model.json.schema

/**
 * A value of the given type, or `null`.
 */
class JsonNullableType(
    type: JsonType,
    nullDescription: String,
) : AbstractJsonType() {

    val anyOf: List<JsonType> = listOf(type, JsonNullType(nullDescription))

}

class JsonNullType(description: String) : AbstractJsonBaseType("null", description)
