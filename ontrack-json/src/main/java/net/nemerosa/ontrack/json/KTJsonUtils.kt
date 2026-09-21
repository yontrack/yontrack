package net.nemerosa.ontrack.json

import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.exc.MismatchedInputException
import tools.jackson.databind.node.*
import java.io.IOException
import java.time.LocalDate
import kotlin.reflect.KClass

private val mapper = ObjectMapperFactory.create()

/**
 * Parses a string as JSON
 */
fun String.parseAsJson(): JsonNode =
    try {
        mapper.readTree(this)
    } catch (e: IOException) {
        throw JsonParseException(e)
    }

/**
 * Map as JSON
 */
fun jsonOf(vararg pairs: Pair<*, *>) =
    mapOf(*pairs).toJson()!!

/**
 * Converts any object into JSON, or null if not defined.
 */
fun <T> T?.toJson(): JsonNode? =
    this?.let { mapper.valueToTree(it) }

/**
 * Non-null JSON transformation
 */
fun <T> T.asJson(): JsonNode = if (this is JsonNode) {
    this
} else {
    mapper.valueToTree(this)
}

/**
 * Transforms the JSON node into a regular object (primitive types, list & maps)
 */
fun JsonNode?.toObject(): Any? =
    if (this != null) {
        when (this) {
            is NullNode -> null
            is BooleanNode -> booleanValue()
            is NumericNode -> numberValue()
            is BinaryNode -> binaryValue()
            is StringNode -> stringValue()

            is ArrayNode -> values().map { it.toObject() }

            is ObjectNode -> properties().asSequence().map { (k, v) ->
                k to v.toObject()
            }.toMap()

            else -> error("Cannot convert JSON to Object: $this")
        }
    } else {
        null
    }

/**
 * Format as a string
 */
fun JsonNode.asJsonString(): String =
    try {
        mapper.writeValueAsString(this)
    } catch (e: JacksonException) {
        throw JsonParseException(e)
    }

/**
 * Parses any node into an object.
 */
inline fun <reified T : Any> JsonNode.parse(): T =
    parseInto(T::class)

/**
 * Parses any node into an object.
 */
fun <T : Any> JsonNode.parseInto(type: KClass<T>): T =
    try {
        mapper.treeToValue(this, type.java)
    } catch (ex: MismatchedInputException) {
        throw JsonParseException(
            userFriendlyMessage(ex)
        )
    } catch (ex: JacksonException) {
        throw JsonParseException(ex)
    }

fun userFriendlyMessage(ex: MismatchedInputException): String {
    val pathDescription = ex.path
        .joinToString(separator = ".") { ref ->
            // A Reference can point to either a field or an array index
            // If ref.fieldName is null, we assume it's an array index
            ref.propertyName ?: "[${ref.index}]"
        }

    // ex.location might be null in some cases, so check for nullability
    val location = ex.location
    val line = location?.lineNr
    val column = location?.columnNr

    return buildString {
        append("There was a problem parsing the JSON")
        if (pathDescription.isNotBlank()) {
            append(" at path '$pathDescription'")
        }
        if (line != null && column != null && line >= 0 && column >= 0) {
            append(" (line $line, column $column)")
        }
        ex.originalMessage?.takeIf { it.isNotBlank() }?.let { message ->
            append(": $message")
        }
    }
}

/**
 * Formatting a JSON node as a string
 */
fun JsonNode.format(): String = try {
    mapper.writeValueAsString(this)
} catch (e: JacksonException) {
    throw JsonParseException(e)
}

/**
 * Parses any node into an object or returns `null` if parsing fails
 */
inline fun <reified T> JsonNode.parseOrNull(): T? =
    try {
        if (this.isNull) {
            null
        } else {
            parse()
        }
    } catch (_: JsonParseException) {
        null
    }

/**
 * Gets a field as enum
 */
inline fun <reified E : Enum<E>> JsonNode.getEnum(field: String): E? {
    val text = path(field).asText()
    if (text.isNullOrBlank()) {
        return null
    } else {
        return enumValueOf<E>(text)
    }
}

/**
 * Gets a field as [Int].
 */
@Deprecated(message = "Use getIntField", replaceWith = ReplaceWith("getIntField"))
fun JsonNode.getInt(field: String): Int? = getIntField(field)

/**
 * Gets a field as a JSON node, but returns `null` if this is a null node.
 */
fun JsonNode.getJsonField(field: String): JsonNode? =
    if (has(field)) {
        get(field)?.takeIf { !it.isNull }
    } else {
        null
    }

/**
 * Gets a required field as a JSON node, but returns `null` if this is a null node.
 */
fun JsonNode.getRequiredJsonField(field: String): JsonNode =
    getJsonField(field)
        ?: throw JsonMissingFieldException(field)

/**
 * Gets a field as [Int].
 */
fun JsonNode.getIntField(field: String): Int? =
    if (has(field)) {
        val value = get(field)
        if (value.isNull) {
            null
        } else if (value.isNumber) {
            value.asInt()
        } else if (value.isTextual) {
            val text = value.asText()
            text.toIntOrNull()
        } else {
            null
        }
    } else {
        null
    }

/**
 * Gets a required field as enum
 */
inline fun <reified E : Enum<E>> JsonNode.getRequiredEnum(field: String): E =
    getEnum<E>(field) ?: throw JsonMissingFieldException(field)

/**
 * Checks if a JSON node can be considered as null, either by being `null` itself
 * or by being an instance of the [null node][JsonNode.isNull].
 */
fun JsonNode?.isNullOrNullNode() = this == null || this.isNull

/**
 * Gets a string field
 */
fun JsonNode.getTextField(field: String): String? = if (has(field)) {
    get(field).takeIf { !it.isNull }?.asText()
} else {
    null
}

/**
 * Gets a LocalDate field
 */
fun JsonNode.getDateField(field: String): LocalDate? =
    if (this.has(field) && !this.get(field).isNull) {
        JDKLocalDateDeserializer.parse(path(field).asText())
    } else {
        null
    }

/**
 * Gets a required string field
 */
fun JsonNode.getRequiredTextField(field: String): String =
    getTextField(field)
        ?: throw JsonParseException("Missing field $field")

/**
 * Gets a boolean field
 */
fun JsonNode.getBooleanField(field: String): Boolean? = if (has(field)) {
    get(field).takeIf { !it.isNull }?.asBoolean()
} else {
    null
}

/**
 * Gets a required boolean field
 */
fun JsonNode.getRequiredBooleanField(field: String): Boolean =
    getBooleanField(field)
        ?: throw JsonParseException("Missing field $field")

/**
 * Gets a required int field
 */
fun JsonNode.getRequiredIntField(field: String): Int =
    getIntField(field)
        ?: throw JsonParseException("Missing field $field")

/**
 * Merging a JSON node into an object.
 *
 * @receiver Object to merge into
 * @param node Node to merge into this object
 * @param priority What to do on identical leaf fields
 * @param arrays What to do on arrays
 * @param conflictResolution What to do when fields do not have the same type
 * @return Result of the merge
 */
inline fun <reified T> T.merge(
    node: JsonNode,
    priority: JsonMergePriority = JsonMergePriority.RIGHT,
    arrays: JsonArrayMergePriority = JsonArrayMergePriority.APPEND,
    conflictResolution: JsonConflictResolution = JsonConflictResolution.ABORT,
): T {
    val target = asJson()
    val result = target.merge(
        node = node,
        priority = priority,
        arrays = arrays,
        conflictResolution = conflictResolution
    )
    return result.parse()
}

/**
 * Merging two JSON nodes
 *
 * @receiver Left component
 * @param node Right component
 * @param priority What to do on identical leaf fields
 * @param arrays What to do on arrays
 * @param conflictResolution What to do when fields do not have the same type
 * @return Result of the merge
 */
fun JsonNode.merge(
    node: JsonNode,
    priority: JsonMergePriority = JsonMergePriority.RIGHT,
    arrays: JsonArrayMergePriority = JsonArrayMergePriority.APPEND,
    conflictResolution: JsonConflictResolution = JsonConflictResolution.ABORT,
): JsonNode =
    when {
        node::class == this::class -> {
            when (this) {
                is ObjectNode -> mergeObject(node as ObjectNode, priority, arrays, conflictResolution)
                is ArrayNode -> mergeArray(node as ArrayNode, arrays)
                else -> when (priority) {
                    JsonMergePriority.LEFT -> this
                    JsonMergePriority.RIGHT -> node
                }
            }
        }

        node.isNullOrNullNode() -> {
            this
        }

        this.isNullOrNullNode() -> {
            node
        }

        else -> {
            when (conflictResolution) {
                JsonConflictResolution.ABORT -> error("Cannot merge JSON because of type conflict.")
                JsonConflictResolution.LEFT -> this
                JsonConflictResolution.RIGHT -> node
            }
        }
    }

fun ArrayNode.mergeArray(
    node: ArrayNode,
    arrays: JsonArrayMergePriority,
): JsonNode = when (arrays) {
    JsonArrayMergePriority.LEFT -> this
    JsonArrayMergePriority.RIGHT -> node
    JsonArrayMergePriority.APPEND -> {
        val target = arrayNode()
        target.addAll(this)
        target.addAll(node)
        target
    }
}

fun ObjectNode.mergeObject(
    node: ObjectNode,
    priority: JsonMergePriority,
    arrays: JsonArrayMergePriority,
    conflictResolution: JsonConflictResolution,
): JsonNode {
    // All field names
    val names = mutableSetOf<String>()
    this.propertyNames().forEach { names += it }
    node.propertyNames().forEach { names += it }
    // Looping over all the fields
    val target = objectNode()
    names.forEach { name ->
        val value: JsonNode? = if (this.has(name) && node.has(name)) {
            this.get(name).merge(node.get(name), priority, arrays, conflictResolution)
        } else if (this.has(name)) {
            this.get(name)
        } else if (node.has(name)) {
            node.get(name)
        } else {
            null
        }
        if (value != null) {
            target.set(name, value)
        }
    }
    // OK
    return target
}

/**
 * What to do in case of type difference
 */
enum class JsonConflictResolution {
    ABORT,
    LEFT,
    RIGHT
}

/**
 * Priorities for JSON merge at field level
 */
enum class JsonMergePriority {
    RIGHT,
    LEFT
}

/**
 * Priorities for JSON merge at array level
 */
enum class JsonArrayMergePriority {
    APPEND,
    RIGHT,
    LEFT
}
