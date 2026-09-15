package net.nemerosa.ontrack.extension.general.validation

import com.fasterxml.jackson.databind.JsonNode
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.structure.ValidationDataTypeAlias
import org.springframework.stereotype.Component

@Component
class ThresholdNumberValidationDataTypeAlias : ValidationDataTypeAlias {
    override val alias: String = "number"
    override val type: String = ThresholdNumberValidationDataType::class.java.name

    override fun parseConfig(data: JsonNode): JsonNode =
        data.parse<ThresholdConfig>().asJson()
}
