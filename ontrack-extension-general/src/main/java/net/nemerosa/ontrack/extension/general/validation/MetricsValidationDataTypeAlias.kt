package net.nemerosa.ontrack.extension.general.validation

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.model.structure.ValidationDataTypeAlias
import org.springframework.stereotype.Component

@Component
class MetricsValidationDataTypeAlias : ValidationDataTypeAlias {
    override val alias: String = "metrics"
    override val type: String = MetricsValidationDataType::class.java.name

    override fun parseConfig(data: JsonNode): JsonNode = data
}