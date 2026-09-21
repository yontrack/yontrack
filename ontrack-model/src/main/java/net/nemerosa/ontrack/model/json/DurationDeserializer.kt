package net.nemerosa.ontrack.model.json

import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.ValueDeserializer
import net.nemerosa.ontrack.common.parseDuration
import java.time.Duration

class DurationDeserializer : ValueDeserializer<Duration>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Duration {
        val value: String = p.readValueAs(String::class.java)
        return parseDuration(value)
            ?: error("Cannot parse duration: $value")
    }
}