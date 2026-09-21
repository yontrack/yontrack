package net.nemerosa.ontrack.json;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class JDKLocalDateTimeDeserializer extends ValueDeserializer<LocalDateTime> {

    @Override
    public LocalDateTime deserialize(JsonParser jp, DeserializationContext ctxt) {
        String s = jp.readValueAs(String.class);
        if (StringUtils.isNotBlank(s)) {
            // Tries first with local date time
            try {
                return LocalDateTime.parse(s, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (DateTimeParseException ignored) {
                // ... with a time zone
                return LocalDateTime.ofInstant(Instant.parse(s), ZoneOffset.UTC);
            }
        } else {
            return null;
        }
    }
}
