package net.nemerosa.ontrack.extension.general.validation

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.NullNode
import net.nemerosa.ontrack.extension.general.GeneralExtensionFeature
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.getTextField
import net.nemerosa.ontrack.model.json.schema.JsonEmptyType
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.model.structure.AbstractValidationDataType
import net.nemerosa.ontrack.model.structure.ValidationRunStatusID
import org.springframework.stereotype.Component

@Component
class TextValidationDataType(
        extensionFeature: GeneralExtensionFeature
) : AbstractValidationDataType<Any?, String>(
        extensionFeature
) {
    override fun configFromJson(node: JsonNode?) {}

    override fun configToJson(config: Any?): JsonNode = NullNode.instance

    override fun createConfigJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType = JsonEmptyType.INSTANCE

    override fun configToFormJson(config: Any?) = null

    override fun fromConfigForm(node: JsonNode?) {}

    override fun toJson(data: String): JsonNode = data.asJson()

    override fun fromJson(node: JsonNode): String? = node.stringValueOpt().orElse(null)

    override fun fromForm(node: JsonNode?): String? = node?.getTextField("value")

    override fun computeStatus(config: Any?, data: String): ValidationRunStatusID? = null

    override fun validateData(config: Any?, data: String?): String {
        return validateNotNull(data)
    }

    override val displayName = "Free text"

    override fun getMetrics(data: String): Map<String, *>? = null
}