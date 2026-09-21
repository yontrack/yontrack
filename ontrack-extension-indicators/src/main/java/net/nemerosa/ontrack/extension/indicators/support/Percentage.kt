package net.nemerosa.ontrack.extension.indicators.support

import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.ValueSerializer
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.annotation.JsonDeserialize
import tools.jackson.databind.annotation.JsonSerialize

/**
 * Representation of a value strictly between 0 and 100.
 */
@JsonSerialize(using = PercentageJsonSerializer::class)
@JsonDeserialize(using = PercentageJsonDeserializer::class)
data class Percentage(
        val value: Int
) {

    init {
        check(value in 0..100) { "Value must be >=0 and <= 100" }
    }

    override fun toString(): String = "$value%"

    fun invert() = Percentage(100 - value)

    operator fun compareTo(percent: Percentage): Int =
            this.value.compareTo(percent.value)
}

fun Int.percent() = Percentage(this)

class PercentageJsonSerializer : ValueSerializer<Percentage>() {
    override fun serialize(value: Percentage, gen: JsonGenerator, serializers: SerializationContext) {
        gen.writeNumber(value.value)
    }
}

class PercentageJsonDeserializer : ValueDeserializer<Percentage>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Percentage =
            p.readValueAs(Int::class.java).percent()

}