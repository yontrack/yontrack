package net.nemerosa.ontrack.extension.general.validation

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.structure.ValidationDataTypeAlias
import org.springframework.stereotype.Component

@Component
class TestSummaryValidationDataTypeAlias : ValidationDataTypeAlias {
    override val alias: String = "tests"
    override val type: String = TestSummaryValidationDataType::class.java.name

    override fun parseConfig(data: JsonNode): JsonNode =
        data.parse<TestSummaryValidationConfig>().asJson()
}