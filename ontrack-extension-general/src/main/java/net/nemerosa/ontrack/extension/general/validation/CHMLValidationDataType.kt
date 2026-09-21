package net.nemerosa.ontrack.extension.general.validation

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.general.GeneralExtensionFeature
import net.nemerosa.ontrack.json.getIntField
import net.nemerosa.ontrack.json.getRequiredEnum
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.json.toJson
import net.nemerosa.ontrack.model.json.schema.*
import net.nemerosa.ontrack.model.structure.AbstractValidationDataType
import net.nemerosa.ontrack.model.structure.MetricsColors
import net.nemerosa.ontrack.model.structure.NumericValidationDataType
import net.nemerosa.ontrack.model.structure.ValidationRunStatusID
import org.springframework.stereotype.Component

/**
 * Validation data based on the critical / high / medium / low numbers.
 */
@Component
class CHMLValidationDataType(
    extensionFeature: GeneralExtensionFeature,
) : AbstractValidationDataType<CHMLValidationDataTypeConfig, CHMLValidationDataTypeData>(
    extensionFeature
), NumericValidationDataType<CHMLValidationDataTypeConfig, CHMLValidationDataTypeData> {
    override val displayName = "Critical / high / medium / low"

    override fun configToJson(config: CHMLValidationDataTypeConfig) = config.toJson()!!

    override fun configFromJson(node: JsonNode?): CHMLValidationDataTypeConfig? = node?.parse()

    override fun configToFormJson(config: CHMLValidationDataTypeConfig?) =
        config?.let {
            mapOf(
                "failedLevel" to it.failedLevel.level,
                "failedValue" to it.failedLevel.value,
                "warningLevel" to it.warningLevel.level,
                "warningValue" to it.warningLevel.value
            ).toJson()
        }

    override fun fromConfigForm(node: JsonNode?) =
        node?.let {
            CHMLValidationDataTypeConfig(
                warningLevel = CHMLLevel(
                    node.getRequiredEnum("warningLevel"),
                    node.getIntField("warningValue") ?: 0
                ),
                failedLevel = CHMLLevel(
                    node.getRequiredEnum("failedLevel"),
                    node.getIntField("failedValue") ?: 0
                )
            )
        }

    override fun createConfigJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType = JsonObjectType(
        title = "CHML",
        description = "CHML thresholds for the validation data",
        properties = mapOf(
            "warningLevel" to JsonEnumType(
                description = "Level for which to raise a warning",
                values = CHML.entries.map { it.name },
            ),
            "warningValue" to JsonIntType("Value for which to raise a warning"),
            "failedLevel" to JsonEnumType(
                description = "Level for which to raise a failure",
                values = CHML.entries.map { it.name },
            ),
            "failedValue" to JsonIntType("Value for which to raise a failure"),
        ),
        required = listOf(
            "warningLevel",
            "failedLevel"
        ),
        additionalProperties = false,
        oneOf = null,
    )

    override fun toJson(data: CHMLValidationDataTypeData) = data.toJson()!!

    override fun fromJson(node: JsonNode): CHMLValidationDataTypeData = node.parse()

    override fun fromForm(node: JsonNode?): CHMLValidationDataTypeData? =
        node?.let {
            CHMLValidationDataTypeData(
                CHML.entries.associateWith { (node.getIntField(it.name) ?: 0) }
            )
        }

    override fun computeStatus(
        config: CHMLValidationDataTypeConfig?,
        data: CHMLValidationDataTypeData,
    ): ValidationRunStatusID? {
        if (config != null) {
            if (config.failedLevel.value > 0) {
                if ((data.levels[config.failedLevel.level] ?: 0) >= config.failedLevel.value) {
                    return ValidationRunStatusID.STATUS_FAILED
                }
            }
            if (config.warningLevel.value > 0) {
                if ((data.levels[config.warningLevel.level] ?: 0) >= config.warningLevel.value) {
                    return ValidationRunStatusID.STATUS_WARNING
                }
            }
            return ValidationRunStatusID.STATUS_PASSED
        } else {
            return null
        }
    }

    override fun validateData(config: CHMLValidationDataTypeConfig?, data: CHMLValidationDataTypeData?) =
        validateNotNull(data) {
            CHML.entries.forEach {
                val value = levels[it]
                if (value != null) {
                    validate(value >= 0, "Value for ${it} must be >= 0")
                }
            }
        }

    override fun getMetrics(data: CHMLValidationDataTypeData): Map<String, *>? {
        return mapOf(
            "critical" to (data.levels[CHML.CRITICAL] ?: 0),
            "high" to (data.levels[CHML.HIGH] ?: 0),
            "medium" to (data.levels[CHML.MEDIUM] ?: 0),
            "low" to (data.levels[CHML.LOW] ?: 0)
        )
    }

    override fun getMetricNames(): List<String> = CHML.entries.map { it.name.lowercase() }

    override fun getMetricColors(): List<String> = listOf(
        MetricsColors.FAILURE,
        MetricsColors.WARNING,
        MetricsColors.NEUTRAL,
        MetricsColors.SUCCESS,
    )

    override fun getNumericMetrics(data: CHMLValidationDataTypeData): Map<String, Double> {
        return mapOf(
            "critical" to (data.levels[CHML.CRITICAL] ?: 0).toDouble(),
            "high" to (data.levels[CHML.HIGH] ?: 0).toDouble(),
            "medium" to (data.levels[CHML.MEDIUM] ?: 0).toDouble(),
            "low" to (data.levels[CHML.LOW] ?: 0).toDouble()
        )
    }
}

data class CHMLValidationDataTypeData(
    val levels: Map<CHML, Int>,
)

data class CHMLValidationDataTypeConfig(
    val warningLevel: CHMLLevel,
    val failedLevel: CHMLLevel,
)

data class CHMLLevel(
    val level: CHML,
    val value: Int,
) {
    init {
        if (value < 0) throw IllegalArgumentException("Value must be >= 0")
    }
}

enum class CHML(val displayName: String) {
    CRITICAL("Critical"),
    HIGH("High"),
    MEDIUM("Medium"),
    LOW("Low")
}
