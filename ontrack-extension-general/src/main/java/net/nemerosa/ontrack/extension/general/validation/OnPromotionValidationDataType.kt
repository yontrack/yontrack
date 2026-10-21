package net.nemerosa.ontrack.extension.general.validation

import net.nemerosa.ontrack.extension.general.GeneralExtensionFeature
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.getTextField
import net.nemerosa.ontrack.json.isNullOrNullNode
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.exceptions.ValidationRunDataInputException
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.model.json.schema.toType
import net.nemerosa.ontrack.model.structure.AbstractValidationDataType
import net.nemerosa.ontrack.model.structure.ValidationRunStatusID
import org.springframework.stereotype.Component
import com.fasterxml.jackson.databind.JsonNode

/**
 * Validation granted when the build linked to the validated build reaches a promotion.
 *
 * The runs of such a stamp are created by [OnPromotionValidationEventListener], carrying the linked build
 * as their data. A run posted by hand carries no data, and its status is the one given.
 */
@Component
class OnPromotionValidationDataType(
    extensionFeature: GeneralExtensionFeature,
) : AbstractValidationDataType<OnPromotionValidationConfig, OnPromotionValidationData>(
    extensionFeature
) {

    override val displayName: String = "Validation on promotion"

    override fun configToJson(config: OnPromotionValidationConfig): JsonNode = config.asJson()

    override fun configFromJson(node: JsonNode?): OnPromotionValidationConfig? = parseConfig(node)

    override fun configToFormJson(config: OnPromotionValidationConfig?): JsonNode? = config?.asJson()

    override fun fromConfigForm(node: JsonNode?): OnPromotionValidationConfig? = parseConfig(node)

    override fun createConfigJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType =
        jsonTypeBuilder.toType(OnPromotionValidationConfig::class)

    override fun toJson(data: OnPromotionValidationData): JsonNode = data.asJson()

    override fun fromJson(node: JsonNode): OnPromotionValidationData? = node.parse()

    /**
     * A run posted by hand carries no data.
     */
    override fun fromForm(node: JsonNode?): OnPromotionValidationData? = null

    override fun computeStatus(
        config: OnPromotionValidationConfig?,
        data: OnPromotionValidationData
    ): ValidationRunStatusID = ValidationRunStatusID.STATUS_PASSED

    override fun validateData(
        config: OnPromotionValidationConfig?,
        data: OnPromotionValidationData?
    ): OnPromotionValidationData = validateNotNull(data)

    override fun getMetrics(data: OnPromotionValidationData): Map<String, *>? = null

    private fun parseConfig(node: JsonNode?): OnPromotionValidationConfig? =
        if (node.isNullOrNullNode()) {
            null
        } else {
            val project = node!!.getTextField(OnPromotionValidationConfig::project.name)
            val promotion = node.getTextField(OnPromotionValidationConfig::promotion.name)
            validate(!project.isNullOrBlank(), "The project of the linked build is required.")
            validate(!promotion.isNullOrBlank(), "The promotion of the linked build is required.")
            OnPromotionValidationConfig(
                project = project!!,
                qualifier = node.getTextField(OnPromotionValidationConfig::qualifier.name) ?: "",
                promotion = promotion!!,
            )
        }
}
