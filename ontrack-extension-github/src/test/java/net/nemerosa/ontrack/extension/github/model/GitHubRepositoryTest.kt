package net.nemerosa.ontrack.extension.github.model

import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.test.assertEquals

class GitHubRepositoryTest {

    @Test
    fun `Date parsing`() {
        val value = "2013-04-26T14:07:27Z"
        val ldt = LocalDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME)
        assertEquals(2013, ldt.year)
        assertEquals(4, ldt.monthValue)
        assertEquals(26, ldt.dayOfMonth)
        assertEquals(14, ldt.hour)
        assertEquals(7, ldt.minute)
        assertEquals(27, ldt.second)
    }

}