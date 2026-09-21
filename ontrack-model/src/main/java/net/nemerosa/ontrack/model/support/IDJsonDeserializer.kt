package net.nemerosa.ontrack.model.support

import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.ValueDeserializer
import net.nemerosa.ontrack.model.structure.ID

class IDJsonDeserializer : ValueDeserializer<ID>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): ID {
        val value: Int? = p.readValueAs(Int::class.java)
        return if (value != null) ID.of(value) else ID.NONE
    }
}