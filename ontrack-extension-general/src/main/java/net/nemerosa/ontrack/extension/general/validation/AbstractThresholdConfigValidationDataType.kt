package net.nemerosa.ontrack.extension.general.validation

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.general.GeneralExtensionFeature
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.json.toJson
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.model.json.schema.toType
import net.nemerosa.ontrack.model.structure.AbstractValidationDataType
import net.nemerosa.ontrack.model.structure.ValidationRunStatusID

abstract class AbstractThresholdConfigValidationDataType<T>(
    extensionFeature: GeneralExtensionFeature
) : AbstractValidationDataType<ThresholdConfig, T>(
    extensionFeature
) {
    override fun configFromJson(node: JsonNode?): ThresholdConfig? =
        node?.parse()


    override fun createConfigJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType =
        jsonTypeBuilder.toType(ThresholdConfig::class)

    override fun configToJson(config: ThresholdConfig) = config.toJson()!!

    override fun configToFormJson(config: ThresholdConfig?): JsonNode? = config?.toJson()

    override fun fromConfigForm(node: JsonNode?): ThresholdConfig? = node?.parse()

    override fun computeStatus(config: ThresholdConfig?, data: T): ValidationRunStatusID? =
        config?.computeStatus(toIntValue(data))

    protected abstract fun toIntValue(data: T): Int

}