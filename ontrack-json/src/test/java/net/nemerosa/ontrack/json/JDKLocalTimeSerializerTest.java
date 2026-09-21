package net.nemerosa.ontrack.json;

import tools.jackson.core.JacksonException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;


public class JDKLocalTimeSerializerTest {

    @Test
    public void toJson() throws JacksonException {
        assertEquals(
                "\"20:44\"",
                ObjectMapperFactory.create().writeValueAsString(LocalTime.of(20, 44))
        );
    }

    @Test
    public void toJson_null() throws JacksonException {
        assertEquals(
                "{\"time\":null}",
                ObjectMapperFactory.create().writeValueAsString(new LTContainer(null))
        );
    }

    @Test
    public void fromJson() throws IOException {
        assertEquals(
                LocalTime.of(7, 30),
                ObjectMapperFactory.create().readValue("\"07:30\"", LocalTime.class)
        );
    }

    @Test
    public void fromJson_null() throws IOException {
        assertNull(
                ObjectMapperFactory.create().readValue("{\"time\":null}", LTContainer.class).getTime()
        );
    }

    @Test
    public void fromJson_blank() throws IOException {
        assertNull(
                ObjectMapperFactory.create().readValue("{\"time\":\"\"}", LTContainer.class).getTime()
        );
    }

    @Test
    public void fromJson_none() throws IOException {
        assertNull(
                ObjectMapperFactory.create().readValue("{}", LTContainer.class).getTime()
        );
    }

}
