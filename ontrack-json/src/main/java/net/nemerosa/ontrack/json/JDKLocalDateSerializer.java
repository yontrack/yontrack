package net.nemerosa.ontrack.json;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.SerializationContext;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class JDKLocalDateSerializer extends ValueSerializer<LocalDate> {
    @Override
    public void serialize(LocalDate value, JsonGenerator jgen, SerializationContext provider) {
        if (value != null) {
            jgen.writeString(value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        } else {
            jgen.writeNull();
        }
    }
}
