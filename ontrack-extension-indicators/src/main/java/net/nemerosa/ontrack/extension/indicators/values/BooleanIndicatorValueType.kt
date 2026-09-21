package net.nemerosa.ontrack.extension.indicators.values

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.BooleanNode
import net.nemerosa.ontrack.extension.indicators.IndicatorsExtensionFeature
import net.nemerosa.ontrack.extension.indicators.model.IndicatorCompliance
import net.nemerosa.ontrack.extension.indicators.model.IndicatorValueType
import net.nemerosa.ontrack.extension.support.AbstractExtension
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.getBooleanField
import net.nemerosa.ontrack.json.parseOrNull
import org.springframework.stereotype.Component

@Component
class BooleanIndicatorValueType(
        extension: IndicatorsExtensionFeature
) : AbstractExtension(extension), IndicatorValueType<Boolean, BooleanIndicatorValueTypeConfig> {

    override val name: String = "Yes/No"

    override fun status(config: BooleanIndicatorValueTypeConfig, value: Boolean): IndicatorCompliance =
            when {
                value -> IndicatorCompliance.HIGHEST
                config.required -> IndicatorCompliance.LOWEST
                else -> IndicatorCompliance.MEDIUM
            }

    override fun toClientJson(config: BooleanIndicatorValueTypeConfig, value: Boolean): JsonNode =
            mapOf(
                    "value" to toClientValue(value)
            ).asJson()

    override fun fromClientJson(config: BooleanIndicatorValueTypeConfig, value: JsonNode): Boolean? {
        val text = value.path("value").asText()
        return fromClientValue(text)
    }

    override fun fromStoredJson(valueConfig: BooleanIndicatorValueTypeConfig, value: JsonNode): Boolean? = when {
        value.isNull -> null
        value.isBoolean -> value.asBoolean()
        else -> null
    }

    override fun toStoredJson(config: BooleanIndicatorValueTypeConfig, value: Boolean): JsonNode {
        return BooleanNode.valueOf(value)
    }

    companion object {
        private const val CLIENT_NULL = ""
        private const val CLIENT_TRUE = "true"
        private const val CLIENT_FALSE = "false"

        private fun toClientValue(value: Boolean?) =
                when (value) {
                    null -> CLIENT_NULL
                    true -> CLIENT_TRUE
                    else -> CLIENT_FALSE
                }

        private fun fromClientValue(value: String) =
                when (value) {
                    CLIENT_TRUE -> true
                    CLIENT_FALSE -> false
                    else -> null
                }
    }

    override fun toConfigForm(config: BooleanIndicatorValueTypeConfig): JsonNode = config.asJson()

    override fun fromConfigForm(config: JsonNode): BooleanIndicatorValueTypeConfig {
        val required = config.getBooleanField(BooleanIndicatorValueTypeConfig::required.name) ?: true
        return BooleanIndicatorValueTypeConfig(required)
    }

    override fun toConfigClientJson(config: BooleanIndicatorValueTypeConfig): JsonNode =
            config.asJson()

    override fun toConfigStoredJson(config: BooleanIndicatorValueTypeConfig): JsonNode =
            config.asJson()

    override fun fromConfigStoredJson(config: JsonNode): BooleanIndicatorValueTypeConfig =
            config.parseOrNull<BooleanIndicatorValueTypeConfig>()
                    ?: BooleanIndicatorValueTypeConfig(required = true)
}

data class BooleanIndicatorValueTypeConfig(
        val required: Boolean
)