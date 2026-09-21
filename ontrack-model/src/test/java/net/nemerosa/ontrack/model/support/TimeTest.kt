package net.nemerosa.ontrack.model.support

import com.fasterxml.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.common.truncate
import net.nemerosa.ontrack.json.ObjectMapperFactory
import net.nemerosa.ontrack.model.structure.Signature.Companion.of
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TimeTest {

    @Test
    fun truncate() {
        assertEquals(
                LocalDateTime.of(2020, 4, 7, 12, 36, 57, 123400000),
                LocalDateTime.of(2020, 4, 7, 12, 36, 57, 123456789).truncate()
        )
        assertEquals(
                LocalDateTime.of(2020, 4, 7, 12, 36, 57, 123400000),
                LocalDateTime.of(2020, 4, 7, 12, 36, 57, 123400000).truncate()
        )
        assertEquals(
                LocalDateTime.of(2020, 4, 7, 12, 36, 57, 120000000),
                LocalDateTime.of(2020, 4, 7, 12, 36, 57, 120000000).truncate()
        )
        assertEquals(
                LocalDateTime.of(2020, 4, 7, 12, 36, 57),
                LocalDateTime.of(2020, 4, 7, 12, 36, 57).truncate()
        )
    }

    @Test
    fun `Stored format length check`() {
        val time = Time.now()
        val stored = Time.store(time)
        assertTrue(stored.length <= 24, "Stored time must be <= 24 characters longs")
    }

    @Test
    fun `Stored format check`() {
        val time = LocalDateTime.of(2020, 4, 7, 12, 36, 57, 123456789)
        val stored = Time.store(time)
        assertEquals("2020-04-07T12:36:57.1234", stored)
    }

    @Test
    fun `Stored format check shorter`() {
        val time = LocalDateTime.of(2020, 4, 7, 12, 36, 57, 120000000)
        val stored = Time.store(time)
        assertEquals("2020-04-07T12:36:57.12", stored)
    }

    @Test
    fun `Stored format check zero`() {
        val time = LocalDateTime.of(2020, 4, 7, 12, 36, 57)
        val stored = Time.store(time)
        assertEquals("2020-04-07T12:36:57", stored)
    }

    @Test
    fun `Stored format read`() {
        val stored = Time.fromStorage("2020-04-07T12:36:57.0000")
        assertEquals(LocalDateTime.of(2020, 4, 7, 12, 36, 57), stored)
    }

    @Test
    fun `Stored format compatible read`() {
        val stored = Time.fromStorage("2020-04-07T12:36:57")
        assertEquals(LocalDateTime.of(2020, 4, 7, 12, 36, 57), stored)
    }

    @Test
    fun end_to_end() {
        // Server time
        val time = Time.now()
        System.out.format("Server time: %s%n", time)
        // For storage
        val stored = Time.store(time)
        System.out.format("Stored time: %s%n", stored)
        // From storage
        val retrieved = Time.fromStorage(stored)
        System.out.format("Retrieved time: %s%n", retrieved)
        // Out for JSON
        val signature = of(time, "user")
        val jsonNode = ObjectMapperFactory.create().valueToTree<JsonNode>(signature)
        val json = ObjectMapperFactory.create().writeValueAsString(jsonNode)
        System.out.format("JSON output: %s%n", json)
        // Extracts the date from the JSON
        val jsonTime = jsonNode.path("time").asText()
        System.out.format("JSON time: %s%n", jsonTime)
        // Converts to a LocalDateTime
        val parsed = LocalDateTime.parse(jsonTime, DateTimeFormatter.ISO_DATE_TIME)
        System.out.format("Parsed time: %s%n", parsed)
        // Checks equality
        assertEquals(time, parsed, "The initial date & the parsed date must be equal")
    }
}