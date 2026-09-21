package net.nemerosa.ontrack.extension.general.validation

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.general.GeneralExtensionFeature
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.json.toJson
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.model.json.schema.toType
import net.nemerosa.ontrack.model.structure.AbstractValidationDataType
import net.nemerosa.ontrack.model.structure.MetricsColors
import net.nemerosa.ontrack.model.structure.NumericValidationDataType
import net.nemerosa.ontrack.model.structure.ValidationRunStatusID
import org.springframework.stereotype.Component

/**
 * Validation data type associated to a CI test.
 */
@Component
class TestSummaryValidationDataType(
        extensionFeature: GeneralExtensionFeature
) : AbstractValidationDataType<TestSummaryValidationConfig, TestSummaryValidationData>(
        extensionFeature
), NumericValidationDataType<TestSummaryValidationConfig, TestSummaryValidationData> {

    override val displayName: String = "Test summary"

    override fun toJson(data: TestSummaryValidationData): JsonNode = data.toJson()!!

    override fun fromJson(node: JsonNode): TestSummaryValidationData? = node.parse()

    override fun fromForm(node: JsonNode?): TestSummaryValidationData? =
            node?.parse()

    override fun validateData(config: TestSummaryValidationConfig?, data: TestSummaryValidationData?): TestSummaryValidationData =
            validateNotNull(data) {
                validate(passed >= 0, "Count of passed tests must be >= 0")
                validate(skipped >= 0, "Count of skipped tests must be >= 0")
                validate(failed >= 0, "Count of failed tests must be >= 0")
            }

    // Configuration aspects

    override fun configToJson(config: TestSummaryValidationConfig): JsonNode =
            config.toJson()!!

    override fun configFromJson(node: JsonNode?): TestSummaryValidationConfig? =
            node?.parse()

    override fun createConfigJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType =
        jsonTypeBuilder.toType(TestSummaryValidationConfig::class)

    override fun configToFormJson(config: TestSummaryValidationConfig?): JsonNode? =
            config?.toJson()

    override fun fromConfigForm(node: JsonNode?): TestSummaryValidationConfig? =
            node?.parse()

    override fun computeStatus(config: TestSummaryValidationConfig?, data: TestSummaryValidationData): ValidationRunStatusID? {
        return config?.computeStatus(data)


    }

    override fun getMetrics(data: TestSummaryValidationData): Map<String, *>? {
        return mapOf(
                "passed" to data.passed,
                "skipped" to data.skipped,
                "failed" to data.failed,
                "total" to data.total
        )
    }

    override fun getMetricNames(): List<String>? = listOf(
            "passed", "skipped", "failed", "total"
    )

    override fun getMetricColors(): List<String> = listOf(
            MetricsColors.SUCCESS,
            MetricsColors.WARNING,
            MetricsColors.FAILURE,
            MetricsColors.NEUTRAL,
    )

    override fun getNumericMetrics(data: TestSummaryValidationData): Map<String, Double> {
        return mapOf(
            "passed" to data.passed.toDouble(),
            "skipped" to data.skipped.toDouble(),
            "failed" to data.failed.toDouble(),
            "total" to data.total.toDouble()
        )
    }
}