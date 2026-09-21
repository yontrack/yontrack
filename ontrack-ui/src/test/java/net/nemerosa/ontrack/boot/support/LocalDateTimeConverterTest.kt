package net.nemerosa.ontrack.boot.support

import org.junit.jupiter.api.Test
import java.time.temporal.ChronoField
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocalDateTimeConverterTest {

    private val converter = LocalDateTimeConverter()


    @Test
    fun empty_to_null() {
        assertNull(converter.convert(""))
    }

    @Test
    fun blank_to_null() {
        assertNull(converter.convert(" "))
    }

    @Test
    fun utc_with_millis() {
        val t = converter.convert("2016-12-10T19:49:55.100Z")!!
        assertEquals(2016, t.year.toLong())
        assertEquals(12, t.month.value.toLong())
        assertEquals(10, t.dayOfMonth.toLong())
        assertEquals(19, t.hour.toLong())
        assertEquals(49, t.minute.toLong())
        assertEquals(55, t.second.toLong())
        assertEquals(100, t[ChronoField.MILLI_OF_SECOND].toLong())
    }

    @Test
    fun utc_with_seconds() {
        val t = converter.convert("2016-12-10T19:49:55Z")!!
        assertEquals(2016, t.year.toLong())
        assertEquals(12, t.month.value.toLong())
        assertEquals(10, t.dayOfMonth.toLong())
        assertEquals(19, t.hour.toLong())
        assertEquals(49, t.minute.toLong())
        assertEquals(55, t.second.toLong())
        assertEquals(0, t[ChronoField.MILLI_OF_SECOND].toLong())
    }
}