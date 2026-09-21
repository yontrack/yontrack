package net.nemerosa.ontrack.extension.indicators.values

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.indicators.IndicatorsExtensionFeature
import net.nemerosa.ontrack.extension.indicators.model.IndicatorCompliance
import net.nemerosa.ontrack.extension.indicators.model.IndicatorValueType
import net.nemerosa.ontrack.extension.indicators.support.Percentage
import net.nemerosa.ontrack.extension.indicators.support.PercentageThreshold
import net.nemerosa.ontrack.extension.indicators.support.percent
import net.nemerosa.ontrack.extension.support.AbstractExtension
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.getIntField
import net.nemerosa.ontrack.json.parseOrNull
import org.springframework.stereotype.Component

@Component
class PercentageIndicatorValueType(
        extension: IndicatorsExtensionFeature
) : AbstractExtension(extension), IndicatorValueType<Percentage, PercentageThreshold> {

    override val name: String = "Percentage"

    override fun status(config: PercentageThreshold, value: Percentage): IndicatorCompliance =
            IndicatorCompliance(config.getCompliance(value).value)

    override fun toClientJson(config: PercentageThreshold, value: Percentage): JsonNode =
            mapOf("value" to value.value).asJson()

    override fun fromClientJson(config: PercentageThreshold, value: JsonNode): Percentage? {
        val i = value.getIntField("value") ?: -1
        return if (i < 0) {
            null
        } else {
            i.percent()
        }
    }

    override fun fromStoredJson(valueConfig: PercentageThreshold, value: JsonNode): Percentage? =
            if (value.isInt) {
                value.asInt().percent()
            } else {
                null
            }

    override fun toStoredJson(config: PercentageThreshold, value: Percentage): JsonNode =
            value.value.asJson()

    override fun toConfigForm(config: PercentageThreshold): JsonNode =
            config.asJson()

    override fun fromConfigForm(config: JsonNode): PercentageThreshold =
            config.parseOrNull() ?: DEFAULT

    override fun toConfigClientJson(config: PercentageThreshold): JsonNode =
            config.asJson()

    override fun toConfigStoredJson(config: PercentageThreshold): JsonNode =
            config.asJson()

    override fun fromConfigStoredJson(config: JsonNode): PercentageThreshold =
            config.parseOrNull() ?: DEFAULT

    companion object {
        private val DEFAULT = PercentageThreshold(
                threshold = 50.percent(),
                higherIsBetter = true
        )
    }
}
