package net.nemerosa.ontrack.extension.indicators.model

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.NullNode
import net.nemerosa.ontrack.model.structure.Signature

data class Indicator<T>(
    val type: IndicatorType<T, *>,
    val value: T?,
    val compliance: IndicatorCompliance?,
    val comment: String?,
    val signature: Signature
) {
    fun toClientJson(): JsonNode = value?.let { type.toClientJson(it) } ?: NullNode.instance

    fun toClientString(): String = value?.let { type.toClientString(it) } ?: ""

}
