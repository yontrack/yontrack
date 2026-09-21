package net.nemerosa.ontrack.model.json

import tools.jackson.core.JsonGenerator
import tools.jackson.databind.ValueSerializer
import tools.jackson.databind.SerializationContext
import net.nemerosa.ontrack.common.format
import java.time.Duration

class DurationSerializer : ValueSerializer<Duration>() {
    override fun serialize(value: Duration, gen: JsonGenerator, serializers: SerializationContext) {
        gen.writeString(value.format())
    }
}