package net.nemerosa.ontrack.extension.general.validation

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.IntNode
import net.nemerosa.ontrack.extension.general.GeneralExtensionFeature
import net.nemerosa.ontrack.model.structure.NumericValidationDataType
import org.springframework.stereotype.Component

@Component
class ThresholdNumberValidationDataType(
    extensionFeature: GeneralExtensionFeature,
) : AbstractThresholdConfigValidationDataType<Int>(
    extensionFeature
), NumericValidationDataType<ThresholdConfig, Int> {

    override fun toJson(data: Int): JsonNode = IntNode(data)

    override fun fromJson(node: JsonNode): Int? =
        when (node) {
            is IntNode -> node.asInt()
            else -> null
        }

    override fun fromForm(node: JsonNode?): Int? =
        if (node != null && node.has("value")) {
            node.get("value").asInt()
        } else {
            null
        }

    override fun toIntValue(data: Int) = data

    override fun validateData(config: ThresholdConfig?, data: Int?) =
        validateNotNull(data)

    override val displayName = "Numeric data"

    override fun getMetrics(data: Int): Map<String, *>? {
        return mapOf("value" to data)
    }

    override fun getMetricNames(): List<String>? = listOf("value")

    override fun getNumericMetrics(data: Int): Map<String, Double> {
        return mapOf("value" to data.toDouble())
    }
}