package net.nemerosa.ontrack.extension.general.validation

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.IntNode
import net.nemerosa.ontrack.extension.general.GeneralExtensionFeature
import net.nemerosa.ontrack.model.exceptions.ValidationRunDataInputException
import net.nemerosa.ontrack.model.structure.NumericValidationDataType
import org.springframework.stereotype.Component

@Component
class ThresholdPercentageValidationDataType(
    extensionFeature: GeneralExtensionFeature,
) : AbstractThresholdConfigValidationDataType<Int>(
    extensionFeature
), NumericValidationDataType<ThresholdConfig, Int> {

    override fun toJson(data: Int): JsonNode =
        IntNode(data)

    override fun fromJson(node: JsonNode): Int? {
        if (node is IntNode) {
            return node.asInt()
        } else {
            throw ValidationRunDataInputException(
                "Data is expected to be an integer."
            )
        }
    }

    override fun fromForm(node: JsonNode?): Int? {
        if (node != null && node.has("value")) {
            return node.get("value").asInt()
        } else {
            return null
        }
    }

    override fun toIntValue(data: Int) = data

    override fun validateData(config: ThresholdConfig?, data: Int?) =
        validateNotNull(data) {
            validate(this >= 0, "Percentage must be >= 0")
        }

    override val displayName = "Percentage"

    override fun getMetrics(data: Int): Map<String, *>? {
        return mapOf("percentage" to data)
    }

    override fun getMetricNames(): List<String>? = listOf("percentage")

    override fun getNumericMetrics(data: Int): Map<String, Double> {
        return mapOf("percentage" to data.toDouble())
    }
}