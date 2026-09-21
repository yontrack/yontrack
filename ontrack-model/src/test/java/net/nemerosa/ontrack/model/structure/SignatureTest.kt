package net.nemerosa.ontrack.model.structure

import net.nemerosa.ontrack.model.structure.Signature.Companion.of
import net.nemerosa.ontrack.test.TestUtils
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SignatureTest {

    @Test
    fun `Signature are compared until 4 digits of nanoseconds`() {
        val a = of(LocalDateTime.of(2020, 4, 7, 14, 9, 12, 123456789), "test")
        val b = of(LocalDateTime.of(2020, 4, 7, 14, 9, 12, 123477777), "test")
        val c = of(LocalDateTime.of(2020, 4, 7, 14, 9, 12, 123400000), "test")
        assertEquals(a, b, "Don't use more than 4 digits for the nanoseconds comparison")
        assertEquals(a, c, "Don't use more than 4 digits for the nanoseconds comparison")
    }

    @Test
    fun testOf() {
        val s = of("Test")
        assertNotNull(s)
        assertNotNull(s.time)
        assertNotNull(s.user)
        assertEquals("Test", s.user.name)
    }

    @Test
    fun testOfWithDateTime() {
        val s = of(TestUtils.dateTime(), "Test")
        assertNotNull(s)
        assertEquals(TestUtils.dateTime(), s.time)
        assertNotNull(s.user)
        assertEquals("Test", s.user.name)
    }
}