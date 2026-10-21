package net.nemerosa.ontrack.extension.general.validation

import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.structure.ValidationDataTypeAlias
import org.springframework.stereotype.Component
import com.fasterxml.jackson.databind.JsonNode

@Component
class OnPromotionValidationDataTypeAlias(
    private val onPromotionValidationDataType: OnPromotionValidationDataType,
) : ValidationDataTypeAlias {
    override val alias: String = "on-promotion"
    override val type: String = OnPromotionValidationDataType::class.java.name

    override fun parseConfig(data: JsonNode): JsonNode =
        onPromotionValidationDataType.fromConfigForm(data).asJson()
}
