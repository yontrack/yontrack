package net.nemerosa.ontrack.json;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.SerializationContext;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

public class JDKLocalDateTimeSerializer extends ValueSerializer<LocalDateTime> {
    @Override
    public void serialize(LocalDateTime value, JsonGenerator jgen, SerializationContext provider) {
        if (value != null) {
            jgen.writeString(value.toInstant(ZoneOffset.UTC).toString());
        } else {
            jgen.writeNull();
        }
    }
}
