package net.nemerosa.ontrack.it

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.IntNode
import tools.jackson.databind.node.NullNode
import net.nemerosa.ontrack.json.toJson
import net.nemerosa.ontrack.model.json.schema.JsonEmptyType
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.model.structure.AbstractValidationDataType
import net.nemerosa.ontrack.model.structure.ValidationRunStatusID
import org.springframework.stereotype.Component

@Component
class NOPValidationDataType(
        extension: NOPExtensionFeature
) : AbstractValidationDataType<Int?, Int>(
        extension
) {
    override val displayName = "NOP validation data type"

    override fun createConfigJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType = JsonEmptyType.INSTANCE

    override fun configFromJson(node: JsonNode?): Int? =
            when (node) {
                is IntNode -> node.asInt()
                else -> null
            }

    override fun configToJson(config: Int?): JsonNode =
            config?.let { IntNode(it) } ?: NullNode.instance

    override fun configToFormJson(config: Int?): JsonNode? {
        return config?.let { mapOf("threshold" to it).toJson() }
    }

    override fun fromConfigForm(node: JsonNode?): Int? {
        if (node != null && node.has("threshold")) {
            return node.get("threshold").asInt()
        } else {
            return null
        }
    }

    override fun toJson(data: Int): JsonNode = IntNode(data)

    override fun fromJson(node: JsonNode): Int? =
            when (node) {
                is IntNode -> node.asInt()
                else -> null
            }

    override fun fromForm(node: JsonNode?): Int? {
        return if (node?.has("value") == true) {
            node.get("value")?.asInt()
        } else {
            null
        }
    }

    override fun computeStatus(config: Int?, data: Int): ValidationRunStatusID? {
        if (config != null) {
            return if (data > config) {
                ValidationRunStatusID.STATUS_FAILED
            } else {
                ValidationRunStatusID.STATUS_PASSED
            }
        } else {
            return null
        }
    }

    override fun validateData(config: Int?, data: Int?) =
            validateNotNull(data) {
                validate(this >= 0, "Value must be >= 0")
            }

    override fun getMetrics(data: Int): Map<String, *>? = null
}