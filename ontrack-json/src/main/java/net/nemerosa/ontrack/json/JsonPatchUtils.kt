package net.nemerosa.ontrack.json

import tools.jackson.databind.JsonNode
import kotlin.reflect.KProperty0

fun patchString(
    changes: JsonNode,
    property: KProperty0<String>,
): String {
    val name = property.name
    val patchValue = changes.path(name).asText()
    return patchValue
        .takeIf { it.isNotBlank() }
        ?: property.get()
}

fun patchNullableString(
    changes: JsonNode,
    property: KProperty0<String?>,
): String? {
    val name = property.name
    val patchValue = changes.path(name).asText()
    return patchValue
        .takeIf { it.isNotBlank() }
        ?: property.get()
}

fun patchBoolean(
    changes: JsonNode,
    property: KProperty0<Boolean>,
): Boolean {
    val name = property.name
    return if (changes.has(name)) {
        changes.path(name).asBoolean()
    } else {
        property.get()
    }
}

fun patchInt(
    changes: JsonNode,
    property: KProperty0<Int>,
): Int {
    val name = property.name
    return if (changes.has(name)) {
        changes.path(name).asInt()
    } else {
        property.get()
    }
}

inline fun <reified E : Enum<E>> patchEnum(
    changes: JsonNode,
    property: KProperty0<E>,
): E {
    val name = property.name
    val patchValue = changes.path(name).asText()
    return patchValue
        .takeIf { it.isNotBlank() }
        ?.let { enumValueOf<E>(it) }
        ?: property.get()
}

inline fun <reified E : Enum<E>> patchNullableEnum(
    changes: JsonNode,
    property: KProperty0<E?>,
): E? {
    val name = property.name
    val patchValue = changes.path(name).asText()
    return patchValue
        .takeIf { it.isNotBlank() }
        ?.let { enumValueOf<E>(it) }
        ?: property.get()
}

fun patchStringList(
    changes: JsonNode,
    property: KProperty0<List<String>>,
): List<String> {
    val name = property.name
    val patchValue = changes.path(name)
    return if (patchValue.isArray) {
        patchValue.values().map { it.asText() }
    } else {
        property.get()
    }
}
