package net.nemerosa.ontrack.json;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class JDKLocalTimeDeserializer extends ValueDeserializer<LocalTime> {

    @Override
    public LocalTime deserialize(JsonParser jp, DeserializationContext ctxt) {
        String s = jp.readValueAs(String.class);
        if (StringUtils.isNotBlank(s)) {
            return LocalTime.parse(s, DateTimeFormatter.ofPattern("HH:mm"));
        } else {
            return null;
        }
    }
}
