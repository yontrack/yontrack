package net.nemerosa.ontrack.extension.general.validation

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.structure.ValidationDataTypeAlias
import org.springframework.stereotype.Component

@Component
class ThresholdPercentageValidationDataTypeAlias : ValidationDataTypeAlias {
    override val alias: String = "percentage"
    override val type: String = ThresholdPercentageValidationDataType::class.java.name

    override fun parseConfig(data: JsonNode): JsonNode =
        data.parse<ThresholdConfig>().asJson()
}