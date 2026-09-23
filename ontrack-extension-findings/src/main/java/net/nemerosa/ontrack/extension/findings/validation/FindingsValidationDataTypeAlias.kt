package net.nemerosa.ontrack.extension.findings.validation

import net.nemerosa.ontrack.model.structure.ValidationDataTypeAlias
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode

/**
 * `security-findings` alias, for the configuration of a validation stamp in `.yontrack/ci.yaml`.
 * Its configuration is the one of CHML.
 */
@Component
class FindingsValidationDataTypeAlias : ValidationDataTypeAlias {
    override val alias: String = ALIAS
    override val type: String = FindingsValidationDataType::class.java.name

    override fun parseConfig(data: JsonNode): JsonNode = data

    companion object {
        const val ALIAS = "security-findings"
    }
}
