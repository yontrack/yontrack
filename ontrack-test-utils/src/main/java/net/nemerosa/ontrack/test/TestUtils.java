package net.nemerosa.ontrack.test;

import tools.jackson.core.JacksonException;
import tools.jackson.core.TreeNode;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import net.nemerosa.ontrack.json.ObjectMapperFactory;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class TestUtils {

    private static final ObjectMapper mapper = ObjectMapperFactory.create();
    private static final AtomicLong counter = new AtomicLong();

    private TestUtils() {
    }

    public static List<Integer> range(int from, int to) {
        Validate.isTrue(to >= from, "'to' value must be greater or equal to the 'from' value.");
        List<Integer> l = new ArrayList<>();
        for (int i = from; i <= to; i++) {
            l.add(i);
        }
        return l;
    }

    public static @NotNull String uid(@NotNull String prefix) {
        return prefix + new SimpleDateFormat("mmssSSS").format(new Date()) + counter.incrementAndGet();
    }

    public static void assertJsonWrite(JsonNode expectedJson, Object objectToWrite) {
        assertEquals(
                expectedJson,
                mapper.valueToTree(objectToWrite)
        );
    }

    public static void assertJsonWrite(JsonNode expectedJson, Object objectToWrite, Class<?> viewClass) throws JacksonException {
        assertJsonWrite(
                mapper,
                expectedJson,
                objectToWrite,
                viewClass
        );
    }

    public static void assertJsonWrite(ObjectMapper mapper, JsonNode expectedJson, Object objectToWrite, Class<?> viewClass) throws JacksonException {
        assertEquals(
                mapper.writeValueAsString(expectedJson),
                mapper.writerWithView(viewClass).writeValueAsString(objectToWrite)
        );
    }

    public static <T> void assertJsonRead(T expectedResult, JsonNode jsonToRead, Class<T> type) throws JacksonException {
        assertEquals(
                expectedResult,
                mapper.treeToValue(jsonToRead, type)
        );
    }

    public static void assertJsonEquals(TreeNode o1, TreeNode o2) {
        assertEquals(
                o1,
                o2
        );
    }

    public static LocalDateTime dateTime() {
        return LocalDateTime.of(2014, 5, 13, 20, 11);
    }

    public static String dateTimeJson() {
        return "2014-05-13T20:11:00Z";
    }

    public static
    byte[] resourceBytes(@NotNull String path) throws IOException {
        return IOUtils.toByteArray(TestUtils.class.getResource(path));
    }

    public static @NotNull
    String resourceString(@NotNull String path) throws IOException {
        return new String(resourceBytes(path), "UTF-8");
    }

    public static JsonNode resourceJson(String path) throws IOException {
        return mapper.readTree(resourceBytes(path));
    }

}
