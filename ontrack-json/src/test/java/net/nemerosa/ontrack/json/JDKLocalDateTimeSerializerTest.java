package net.nemerosa.ontrack.json;

import tools.jackson.core.JacksonException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;


public class JDKLocalDateTimeSerializerTest {

    @Test
    public void toJson() throws JacksonException {
        assertEquals(
                "\"2014-03-20T20:44:00Z\"",
                ObjectMapperFactory.create().writeValueAsString(LocalDateTime.of(2014, 3, 20, 20, 44))
        );
    }

    @Test
    public void toJson_null() throws JacksonException {
        assertEquals(
                "{\"time\":null}",
                ObjectMapperFactory.create().writeValueAsString(new LDTContainer(null))
        );
    }

    @Test
    public void fromJson_null() throws IOException {
        assertNull(
                ObjectMapperFactory.create().readValue("{}", LDTContainer.class).getTime()
        );
    }

    @Test
    public void fromJson_blank() throws IOException {
        assertNull(
                ObjectMapperFactory.create().readValue("\"\"", LocalDateTime.class)
        );
    }

    @Test
    public void fromJson_with_zone() throws IOException {
        assertEquals(
                LocalDateTime.of(2014, 3, 20, 7, 30),
                ObjectMapperFactory.create().readValue("\"2014-03-20T07:30:00.000Z\"", LocalDateTime.class)
        );
    }

    @Test
    public void fromJson_without_zone() throws IOException {
        assertEquals(
                LocalDateTime.of(2014, 3, 20, 7, 30),
                ObjectMapperFactory.create().readValue("\"2014-03-20T07:30:00.000\"", LocalDateTime.class)
        );
    }

    @Test
    public void fromJson_without_millis() throws IOException {
        assertEquals(
                LocalDateTime.of(2014, 3, 20, 7, 30),
                ObjectMapperFactory.create().readValue("\"2014-03-20T07:30:00\"", LocalDateTime.class)
        );
    }

    @Test
    public void fromJson_without_seconds() throws IOException {
        assertEquals(
                LocalDateTime.of(2014, 3, 20, 7, 30),
                ObjectMapperFactory.create().readValue("\"2014-03-20T07:30\"", LocalDateTime.class)
        );
    }

}
