package net.nemerosa.ontrack.json

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonView
import org.junit.jupiter.api.Test
import java.time.*
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins what [ObjectMapperFactory] writes and reads, so that the Jackson 2 → 3 migration (#1843)
 * keeps it byte for byte. Written on Jackson 2, and meant to pass unchanged on Jackson 3: it names
 * no Jackson type besides the annotations, which both versions share.
 *
 * A failure here is a change of the stored or exchanged JSON format, not a test to update.
 */
class ObjectMapperFactoryCharacterizationTest {

    private val mapper = ObjectMapperFactory.create()

    private fun write(value: Any?): String = mapper.writeValueAsString(value)

    private fun <T> read(json: String, type: Class<T>): T = mapper.readValue(json, type)

    // Dates & times

    @Test
    fun `LocalDateTime is written as a UTC instant`() {
        assertEquals(
            "\"2014-03-20T20:44:00Z\"",
            write(LocalDateTime.of(2014, 3, 20, 20, 44))
        )
        assertEquals(
            "\"2014-03-20T20:44:12.345678Z\"",
            write(LocalDateTime.of(2014, 3, 20, 20, 44, 12, 345_678_000))
        )
    }

    @Test
    fun `LocalDateTime is read with or without a zone`() {
        val expected = LocalDateTime.of(2014, 3, 20, 20, 44, 12, 345_000_000)
        assertEquals(expected, read("\"2014-03-20T20:44:12.345Z\"", LocalDateTime::class.java))
        assertEquals(expected, read("\"2014-03-20T20:44:12.345\"", LocalDateTime::class.java))
        assertNull(read("\"\"", LocalDateTime::class.java))
    }

    @Test
    fun `LocalDate round-trips as an ISO date`() {
        assertEquals("\"2014-03-20\"", write(LocalDate.of(2014, 3, 20)))
        assertEquals(LocalDate.of(2014, 3, 20), read("\"2014-03-20\"", LocalDate::class.java))
    }

    @Test
    fun `LocalTime round-trips as hours and minutes`() {
        assertEquals("\"20:44\"", write(LocalTime.of(20, 44)))
        assertEquals(LocalTime.of(20, 44), read("\"20:44\"", LocalTime::class.java))
    }

    @Test
    fun `YearMonth round-trips as an object`() {
        assertEquals("{\"year\":2014,\"month\":3}", write(YearMonth.of(2014, 3)))
        assertEquals(YearMonth.of(2014, 3), read("{\"year\":2014,\"month\":3}", YearMonth::class.java))
    }

    @Test
    fun `Duration round-trips as decimal seconds`() {
        assertEquals("90.500000000", write(Duration.ofSeconds(90, 500_000_000)))
        assertEquals(Duration.ofSeconds(90, 500_000_000), read("90.5", Duration::class.java))
        assertEquals(Duration.ofMinutes(2), read("\"PT2M\"", Duration::class.java))
    }

    @Test
    fun `Dates inside a bean`() {
        val bean = CharacterizationDates(
            dateTime = LocalDateTime.of(2014, 3, 20, 20, 44),
            date = LocalDate.of(2014, 3, 20),
            time = LocalTime.of(20, 44),
            yearMonth = YearMonth.of(2014, 3),
            duration = Duration.ofSeconds(90),
        )
        val json =
            """{"dateTime":"2014-03-20T20:44:00Z","date":"2014-03-20","time":"20:44","yearMonth":{"year":2014,"month":3},"duration":90.000000000}"""
        assertEquals(json, write(bean))
        assertEquals(bean, read(json, CharacterizationDates::class.java))
    }

    // Enums

    @Test
    fun `Enums round-trip by name`() {
        assertEquals("\"SECOND_VALUE\"", write(CharacterizationEnum.SECOND_VALUE))
        assertEquals(CharacterizationEnum.SECOND_VALUE, read("\"SECOND_VALUE\"", CharacterizationEnum::class.java))
        assertEquals(
            "{\"value\":\"FIRST\"}",
            write(CharacterizationEnumHolder(CharacterizationEnum.FIRST))
        )
    }

    // Kotlin

    @Test
    fun `Kotlin nullable and default parameters`() {
        assertEquals(
            CharacterizationKotlin(name = "n", optional = null, withDefault = 10),
            read("{\"name\":\"n\"}", CharacterizationKotlin::class.java)
        )
        assertEquals(
            CharacterizationKotlin(name = "n", optional = "o", withDefault = 20),
            read("{\"name\":\"n\",\"optional\":\"o\",\"withDefault\":20}", CharacterizationKotlin::class.java)
        )
        assertEquals(
            "{\"name\":\"n\",\"optional\":null,\"withDefault\":10}",
            write(CharacterizationKotlin(name = "n"))
        )
    }

    @Test
    fun `Properties keep their declaration order`() {
        assertEquals(
            "{\"zeta\":1,\"alpha\":2,\"mu\":3}",
            write(CharacterizationOrder(zeta = 1, alpha = 2, mu = 3))
        )
    }

    @Test
    fun `A single value is accepted as an array`() {
        assertEquals(
            CharacterizationList(listOf("one")),
            read("{\"items\":\"one\"}", CharacterizationList::class.java)
        )
    }

    @Test
    fun `Unknown properties are rejected`() {
        kotlin.test.assertFails {
            read("{\"name\":\"n\",\"unknown\":true}", CharacterizationKotlin::class.java)
        }
    }

    @Test
    fun `A null is accepted for a primitive`() {
        assertEquals(
            CharacterizationPrimitive(0),
            read("{\"count\":null}", CharacterizationPrimitive::class.java)
        )
    }

    // Inclusion

    @Test
    fun `NON_NULL types leave their null properties out`() {
        assertEquals("{\"name\":\"n\"}", write(CharacterizationNonNull(name = "n", optional = null)))
        assertEquals("{\"name\":\"n\",\"optional\":\"o\"}", write(CharacterizationNonNull(name = "n", optional = "o")))
    }

    @Test
    fun `Maps keep their nulls`() {
        assertEquals("{\"a\":null,\"b\":1}", write(linkedMapOf("a" to null, "b" to 1)))
    }

    // Views

    @Test
    fun `No view writes every property`() {
        assertEquals(
            "{\"always\":\"a\",\"inView\":\"v\",\"inOtherView\":\"o\"}",
            write(CharacterizationViews("a", "v", "o"))
        )
    }

    @Test
    fun `A view keeps the properties without a view and the ones of this view`() {
        assertEquals(
            "{\"always\":\"a\",\"inView\":\"v\"}",
            mapper.writerWithView(CharacterizationView::class.java)
                .writeValueAsString(CharacterizationViews("a", "v", "o"))
        )
    }

    // JSON utilities

    @Test
    fun `asJson and parse round-trip`() {
        val bean = CharacterizationDates(
            dateTime = LocalDateTime.of(2014, 3, 20, 20, 44),
            date = LocalDate.of(2014, 3, 20),
            time = LocalTime.of(20, 44),
            yearMonth = YearMonth.of(2014, 3),
            duration = Duration.ofSeconds(90),
        )
        val json = bean.asJson()
        assertEquals("\"2014-03-20T20:44:00Z\"", json.path("dateTime").toString())
        assertEquals(bean, json.parse<CharacterizationDates>())
    }

    @Test
    fun `asJsonString is compact`() {
        assertEquals(
            "{\"name\":\"n\",\"list\":[1,2],\"nested\":{\"flag\":true}}",
            mapOf("name" to "n", "list" to listOf(1, 2), "nested" to mapOf("flag" to true)).asJson().asJsonString()
        )
    }

    @Test
    fun `Numbers keep their type`() {
        assertEquals(
            "{\"int\":1,\"long\":10000000000,\"double\":1.5,\"decimal\":1.50}",
            write(
                linkedMapOf(
                    "int" to 1,
                    "long" to 10_000_000_000L,
                    "double" to 1.5,
                    "decimal" to java.math.BigDecimal("1.50"),
                )
            )
        )
    }
}

data class CharacterizationDates(
    val dateTime: LocalDateTime,
    val date: LocalDate,
    val time: LocalTime,
    val yearMonth: YearMonth,
    val duration: Duration,
)

enum class CharacterizationEnum {
    FIRST,
    SECOND_VALUE,
}

data class CharacterizationEnumHolder(
    val value: CharacterizationEnum,
)

data class CharacterizationKotlin(
    val name: String,
    val optional: String? = null,
    val withDefault: Int = 10,
)

data class CharacterizationOrder(
    val zeta: Int,
    val alpha: Int,
    val mu: Int,
)

data class CharacterizationList(
    val items: List<String>,
)

class CharacterizationPrimitive(
    @JvmField val count: Int,
) {
    override fun equals(other: Any?): Boolean = other is CharacterizationPrimitive && other.count == count
    override fun hashCode(): Int = count
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class CharacterizationNonNull(
    val name: String,
    val optional: String?,
)

interface CharacterizationView

interface CharacterizationOtherView

data class CharacterizationViews(
    val always: String,
    @get:JsonView(CharacterizationView::class)
    val inView: String,
    @get:JsonView(CharacterizationOtherView::class)
    val inOtherView: String,
)
