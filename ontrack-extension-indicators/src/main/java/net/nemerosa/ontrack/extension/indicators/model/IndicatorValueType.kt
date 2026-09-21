package net.nemerosa.ontrack.extension.indicators.model

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.model.extension.Extension

interface IndicatorValueType<T, C> : Extension {

    /**
     * Display name
     */
    val name: String

    fun status(config: C, value: T): IndicatorCompliance

    fun toClientJson(config: C, value: T): JsonNode
    fun fromClientJson(config: C, value: JsonNode): T?

    /**
     * Gets a string representation for the value
     */
    fun toClientString(config: C, value: T): String = value.toString()

    fun fromStoredJson(valueConfig: C, value: JsonNode): T?
    fun toStoredJson(config: C, value: T): JsonNode

    fun toConfigForm(config: C): JsonNode
    fun fromConfigForm(config: JsonNode): C

    fun toConfigClientJson(config: C): JsonNode
    fun toConfigStoredJson(config: C): JsonNode
    fun fromConfigStoredJson(config: JsonNode): C

}

/**
 * ID if the FQCN of the value type.
 */
val IndicatorValueType<*, *>.id: String get() = this::class.java.name
