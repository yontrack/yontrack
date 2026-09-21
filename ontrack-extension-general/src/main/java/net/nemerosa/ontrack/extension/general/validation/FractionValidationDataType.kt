package net.nemerosa.ontrack.extension.general.validation

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.general.GeneralExtensionFeature
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.json.toJson
import org.springframework.stereotype.Component

data class FractionValidationData(val numerator: Int, val denominator: Int)

@Component
class FractionValidationDataType(
        extensionFeature: GeneralExtensionFeature
) : AbstractThresholdConfigValidationDataType<FractionValidationData>(
        extensionFeature
) {

    override fun toJson(data: FractionValidationData): JsonNode =
            data.toJson()!!

    override fun fromJson(node: JsonNode): FractionValidationData? =
            node.parse()

    override fun fromForm(node: JsonNode?): FractionValidationData? =
            node?.parse()

    override fun toIntValue(data: FractionValidationData): Int {
        return (data.numerator * 100 / data.denominator)
    }

    override fun validateData(config: ThresholdConfig?, data: FractionValidationData?) =
            validateNotNull(data) {
                validate(numerator >= 0, "Numerator must be >= 0")
                validate(denominator > 0, "Denominator must be > 0")
                validate(numerator <= denominator, "Numerator must <= denominator")
            }

    override val displayName = "Fraction"

    override fun getMetrics(data: FractionValidationData): Map<String, *>? {
        return mapOf(
                "numerator" to data.numerator,
                "denominator" to data.denominator
        )
    }
}