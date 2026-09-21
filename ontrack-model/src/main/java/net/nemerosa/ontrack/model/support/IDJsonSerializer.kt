package net.nemerosa.ontrack.model.support

import tools.jackson.core.JsonGenerator
import tools.jackson.databind.ValueSerializer
import tools.jackson.databind.SerializationContext
import net.nemerosa.ontrack.model.structure.ID

class IDJsonSerializer : ValueSerializer<ID>() {

    override fun serialize(value: ID?, jgen: JsonGenerator, provider: SerializationContext) {
        if (value != null) {
            jgen.writeNumber(value.value)
        } else {
            jgen.writeNull()
        }
    }
}