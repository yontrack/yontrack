package net.nemerosa.ontrack.json;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.time.YearMonth;

public class JDKYearMonthDeserializer extends ValueDeserializer<YearMonth> {
    @Override
    public YearMonth deserialize(JsonParser jp, DeserializationContext ctxt) {
        JsonNode node = jp.readValueAsTree();
        int year = node.path("year").asInt();
        int month = node.path("month").asInt();
        return YearMonth.of(year, month);
    }
}
