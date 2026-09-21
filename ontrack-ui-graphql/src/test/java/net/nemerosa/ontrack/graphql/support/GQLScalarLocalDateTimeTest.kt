package net.nemerosa.ontrack.graphql.support

import net.nemerosa.ontrack.test.assertIs
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals

class GQLScalarLocalDateTimeTest {

    @Test
    fun `Parsing input value`() {
        val ldt = GQLScalarLocalDateTime.INSTANCE.coercing.parseValue("2021-07-31T22:00:00.000Z")
        assertIs<LocalDateTime>(ldt) {
            assertEquals(2021, it.year)
            assertEquals(7, it.monthValue)
            assertEquals(31, it.dayOfMonth)
            assertEquals(22, it.hour)
            assertEquals(0, it.minute)
            assertEquals(0, it.second)
        }
    }

}