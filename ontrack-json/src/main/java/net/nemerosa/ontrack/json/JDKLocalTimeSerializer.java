package net.nemerosa.ontrack.json;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.SerializationContext;

import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class JDKLocalTimeSerializer extends ValueSerializer<LocalTime> {
    @Override
    public void serialize(LocalTime value, JsonGenerator jgen, SerializationContext provider) {
        if (value != null) {
            jgen.writeString(value.format(DateTimeFormatter.ofPattern("HH:mm")));
        } else {
            jgen.writeNull();
        }
    }
}
