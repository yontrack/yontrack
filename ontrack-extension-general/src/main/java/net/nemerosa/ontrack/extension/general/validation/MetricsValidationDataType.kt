package net.nemerosa.ontrack.extension.general.validation

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.NullNode
import net.nemerosa.ontrack.extension.general.GeneralExtensionFeature
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.json.toJson
import net.nemerosa.ontrack.model.json.schema.JsonEmptyType
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.model.structure.AbstractValidationDataType
import net.nemerosa.ontrack.model.structure.NumericValidationDataType
import net.nemerosa.ontrack.model.structure.ValidationRunStatusID
import net.nemerosa.ontrack.model.support.NameValue
import org.springframework.stereotype.Component

@Component
class MetricsValidationDataType(
    extensionFeature: GeneralExtensionFeature,
) : AbstractValidationDataType<Any?, MetricsValidationData>(extensionFeature),
    NumericValidationDataType<Any?, MetricsValidationData> {

    override val displayName: String = "Metrics"

    override fun configToJson(config: Any?): JsonNode = NullNode.instance

    override fun createConfigJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType = JsonEmptyType.INSTANCE

    override fun configFromJson(node: JsonNode?) {}

    override fun configToFormJson(config: Any?): JsonNode? = null

    override fun fromConfigForm(node: JsonNode?) {}

    override fun toJson(data: MetricsValidationData): JsonNode = data.toJson()!!

    override fun fromJson(node: JsonNode): MetricsValidationData? = node.parse()

    override fun fromForm(node: JsonNode?): MetricsValidationData? =
        node?.parse<MetricsValidationDataForm>()?.run {
            MetricsValidationData(
                metrics = metrics.associate { nv -> fromFormItem(nv) }
            )
        }

    private fun fromFormItem(nv: NameValue): Pair<String, Double> {
        val name = nv.name
        val rawValue = nv.value
        return try {
            name to rawValue.toDouble()
        } catch (ex: NumberFormatException) {
            throw MetricsValidationDataNumberFormatException(name, rawValue, ex)
        }
    }

    override fun computeStatus(config: Any?, data: MetricsValidationData): ValidationRunStatusID? = null

    override fun validateData(config: Any?, data: MetricsValidationData?): MetricsValidationData =
        validateNotNull(data)

    override fun getMetrics(data: MetricsValidationData): Map<String, *>? =
        data.metrics

    override fun getMetricNames(): List<String>? = null

    override fun getNumericMetrics(data: MetricsValidationData): Map<String, Double> = data.metrics

    class MetricsValidationDataForm(
        val metrics: List<NameValue>,
    )
}