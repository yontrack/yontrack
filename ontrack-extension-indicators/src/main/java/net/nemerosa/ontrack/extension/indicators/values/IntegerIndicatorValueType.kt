package net.nemerosa.ontrack.extension.indicators.values

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.indicators.IndicatorsExtensionFeature
import net.nemerosa.ontrack.extension.indicators.model.IndicatorCompliance
import net.nemerosa.ontrack.extension.indicators.model.IndicatorValueType
import net.nemerosa.ontrack.extension.indicators.support.IntegerThresholds
import net.nemerosa.ontrack.extension.support.AbstractExtension
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.getIntField
import net.nemerosa.ontrack.json.parseOrNull
import org.springframework.stereotype.Component

@Component
class IntegerIndicatorValueType(
        extension: IndicatorsExtensionFeature
) : AbstractExtension(extension), IndicatorValueType<Int, IntegerThresholds> {

    override val name: String = "Number"

    override fun status(config: IntegerThresholds, value: Int): IndicatorCompliance =
            IndicatorCompliance(config.getCompliance(value).value)

    override fun toClientJson(config: IntegerThresholds, value: Int): JsonNode =
            mapOf("value" to value).asJson()

    override fun fromClientJson(config: IntegerThresholds, value: JsonNode): Int? {
        val i = value.getIntField("value") ?: -1
        return if (i < 0) {
            null
        } else {
            i
        }
    }

    override fun fromStoredJson(valueConfig: IntegerThresholds, value: JsonNode): Int? =
            if (value.isInt) {
                value.asInt()
            } else {
                null
            }

    override fun toStoredJson(config: IntegerThresholds, value: Int): JsonNode =
            value.asJson()

    override fun toConfigForm(config: IntegerThresholds): JsonNode =
            config.asJson()

    override fun fromConfigForm(config: JsonNode): IntegerThresholds =
            config.parseOrNull() ?: DEFAULT

    override fun toConfigClientJson(config: IntegerThresholds): JsonNode =
            config.asJson()

    override fun toConfigStoredJson(config: IntegerThresholds): JsonNode =
            config.asJson()

    override fun fromConfigStoredJson(config: JsonNode): IntegerThresholds =
            config.parseOrNull() ?: DEFAULT

    companion object {
        private val DEFAULT = IntegerThresholds(
                min = 0,
                max = 10,
                higherIsBetter = false
        )
    }
}
