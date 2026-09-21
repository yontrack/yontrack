package net.nemerosa.ontrack.json;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.SerializationContext;

import java.io.IOException;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;

public class JDKYearMonthSerializer extends ValueSerializer<YearMonth> {

    @Override
    public void serialize(YearMonth value, JsonGenerator jgen, SerializationContext provider) {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("year", value.getYear());
        map.put("month", value.getMonthValue());
        provider.writeValue(jgen, map);
    }

}
