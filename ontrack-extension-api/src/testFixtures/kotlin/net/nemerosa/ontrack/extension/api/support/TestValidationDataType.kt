package net.nemerosa.ontrack.extension.api.support

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.NullNode
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.json.parseOrNull
import net.nemerosa.ontrack.model.json.schema.JsonEmptyType
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.model.structure.AbstractValidationDataType
import net.nemerosa.ontrack.model.structure.ValidationRunStatusID
import org.springframework.stereotype.Component


/**
 * Configuration data - none
 */

/**
 * Data
 */
data class TestValidationData(
    val critical: Int = 0,
    val high: Int = 0,
    val medium: Int = 0
)

/**
 * Validation type
 */
@Component
class TestValidationDataType(
    extensionFeature: TestExtensionFeature
) : AbstractValidationDataType<Any, TestValidationData>(
    extensionFeature
) {

    override fun validateData(config: Any?, data: TestValidationData?) =
        validateNotNull(data) {
            validate(critical >= 0, "Number of critical issues must be >= 0")
            validate(high >= 0, "Number of high issues must be >= 0")
            validate(medium >= 0, "Number of medium issues must be >= 0")
        }

    override fun createConfigJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType = JsonEmptyType.INSTANCE

    override fun configToJson(config: Any): JsonNode = NullNode.instance

    override fun configFromJson(node: JsonNode?): Any? = null

    override fun configToFormJson(config: Any?) = null

    override fun fromConfigForm(node: JsonNode?) {}

    override fun toJson(data: TestValidationData): JsonNode = data.asJson()

    override fun fromJson(node: JsonNode): TestValidationData? = node.parseOrNull()

    override fun fromForm(node: JsonNode?): TestValidationData? =
        node?.run {
            parse()
        }

    override fun computeStatus(config: Any?, data: TestValidationData): ValidationRunStatusID? =
        when {
            data.critical > 0 -> ValidationRunStatusID.STATUS_FAILED
            data.high > 0 -> ValidationRunStatusID.STATUS_WARNING
            else -> ValidationRunStatusID.STATUS_PASSED
        }

    override val displayName = "Test validation data"

    override fun getMetrics(data: TestValidationData): Map<String, *>? {
        return mapOf(
            "critical" to data.critical,
            "high" to data.high,
            "medium" to data.medium
        )
    }
}