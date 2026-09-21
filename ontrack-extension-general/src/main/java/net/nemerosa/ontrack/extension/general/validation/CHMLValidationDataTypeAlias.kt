package net.nemerosa.ontrack.extension.general.validation

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.model.structure.ValidationDataTypeAlias
import org.springframework.stereotype.Component

@Component
class CHMLValidationDataTypeAlias : ValidationDataTypeAlias {
    override val alias: String = "chml"
    override val type: String = CHMLValidationDataType::class.java.name

    override fun parseConfig(data: JsonNode): JsonNode = data
}