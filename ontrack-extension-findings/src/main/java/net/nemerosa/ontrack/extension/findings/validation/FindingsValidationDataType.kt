package net.nemerosa.ontrack.extension.findings.validation

import net.nemerosa.ontrack.extension.findings.FindingsExtensionFeature
import net.nemerosa.ontrack.extension.general.validation.CHML
import net.nemerosa.ontrack.extension.general.validation.CHMLValidationDataType
import net.nemerosa.ontrack.extension.general.validation.CHMLValidationDataTypeConfig
import net.nemerosa.ontrack.extension.general.validation.CHMLValidationDataTypeData
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.json.toJson
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.model.structure.AbstractValidationDataType
import net.nemerosa.ontrack.model.structure.NumericValidationDataType
import net.nemerosa.ontrack.model.structure.ValidationRunStatusID
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode

/**
 * Validation data of a security scan whose findings were posted through
 * `validateBuildWithFindings`.
 *
 * Its configuration is the one of CHML — warning and failure thresholds on a severity — and its
 * status is computed as CHML computes it, on the counts of the findings which are not accepted.
 * UNKNOWN findings are counted and shown, but never trip a threshold, since CHML has no such level.
 *
 * The run data holds the counts only, under the keys of CHML plus `unknown` and `accepted`; the
 * findings themselves are in their own tables.
 */
@Component
class FindingsValidationDataType(
    extensionFeature: FindingsExtensionFeature,
    private val chml: CHMLValidationDataType,
) : AbstractValidationDataType<CHMLValidationDataTypeConfig, FindingsValidationDataTypeData>(extensionFeature),
    NumericValidationDataType<CHMLValidationDataTypeConfig, FindingsValidationDataTypeData> {

    override val displayName = "Security findings"

    override fun configToJson(config: CHMLValidationDataTypeConfig): JsonNode = chml.configToJson(config)

    override fun configFromJson(node: JsonNode?): CHMLValidationDataTypeConfig? = chml.configFromJson(node)

    override fun configToFormJson(config: CHMLValidationDataTypeConfig?): JsonNode? = chml.configToFormJson(config)

    override fun fromConfigForm(node: JsonNode?): CHMLValidationDataTypeConfig? = chml.fromConfigForm(node)

    override fun createConfigJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType =
        chml.createConfigJsonType(jsonTypeBuilder)

    override fun toJson(data: FindingsValidationDataTypeData): JsonNode = data.toJson()!!

    override fun fromJson(node: JsonNode): FindingsValidationDataTypeData = node.parse()

    /**
     * The counts go with the findings, which only `validateBuildWithFindings` posts: a run
     * created from a form carries no data, and needs a status.
     */
    override fun fromForm(node: JsonNode?): FindingsValidationDataTypeData? = null

    override fun computeStatus(
        config: CHMLValidationDataTypeConfig?,
        data: FindingsValidationDataTypeData,
    ): ValidationRunStatusID? = chml.computeStatus(config, data.asCHML())

    override fun validateData(
        config: CHMLValidationDataTypeConfig?,
        data: FindingsValidationDataTypeData?,
    ): FindingsValidationDataTypeData =
        validateNotNull(data) {
            levels.forEach { (level, count) ->
                validate(count >= 0, "Count for $level must be >= 0")
            }
            validate(unknown >= 0, "Count of unknown severity must be >= 0")
            validate(accepted >= 0, "Count of accepted findings must be >= 0")
        }

    override fun getMetrics(data: FindingsValidationDataTypeData): Map<String, *>? = chml.getMetrics(data.asCHML())

    override fun getMetricNames(): List<String> = chml.getMetricNames()

    override fun getMetricColors(): List<String> = chml.getMetricColors()

    override fun getNumericMetrics(data: FindingsValidationDataTypeData): Map<String, Double> =
        chml.getNumericMetrics(data.asCHML())
}

/**
 * Counts of the findings of a scan.
 *
 * @property levels Findings which are not accepted, per severity, under the keys of CHML
 * @property unknown Findings which are not accepted and whose severity is UNKNOWN
 * @property accepted Findings under an acceptance holding at the time of the scan, whatever their
 * severity. They are neither open nor resolved, and never trip a threshold.
 */
data class FindingsValidationDataTypeData(
    val levels: Map<CHML, Int>,
    val unknown: Int = 0,
    val accepted: Int = 0,
) {
    /**
     * The same counts, as CHML data
     */
    fun asCHML() = CHMLValidationDataTypeData(levels)
}
