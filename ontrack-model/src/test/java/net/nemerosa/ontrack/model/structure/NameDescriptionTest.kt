package net.nemerosa.ontrack.model.structure

import net.nemerosa.ontrack.model.structure.NameDescription.Companion.escapeName
import org.junit.jupiter.api.Test
import java.util.regex.Pattern
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NameDescriptionTest {

    @Test
    fun equality() {
        assertEquals(NameDescription.nd("a", "A a"), NameDescription.nd("a", "A a"))
    }

    @Test
    fun `Name pattern`() {
        assertTrue(Pattern.matches(NameDescription.NAME, "Test"))
        assertTrue(Pattern.matches(NameDescription.NAME, "2"))
        assertTrue(Pattern.matches(NameDescription.NAME, "2.0.0"))
        assertTrue(Pattern.matches(NameDescription.NAME, "2.0.0-alpha"))
        assertTrue(Pattern.matches(NameDescription.NAME, "2.0.0-alpha-1"))
        assertTrue(Pattern.matches(NameDescription.NAME, "2.0.0-alpha-1-14"))
        assertFalse(Pattern.matches(NameDescription.NAME, "2.0.0-alpha 1-14"))
        assertTrue(Pattern.matches(NameDescription.NAME, "TEST"))
        assertTrue(Pattern.matches(NameDescription.NAME, "TEST_1"))
        assertTrue(Pattern.matches(NameDescription.NAME, "TEST_ONE"))
        assertFalse(Pattern.matches(NameDescription.NAME, "TEST ONE"))
    }

    @Test
    fun `Escaping ok`() {
        assertEquals("2.0.0-beta-12", escapeName("2.0.0-beta-12"))
    }

    @Test
    fun `Escaping special characters`() {
        assertEquals("2.0.0-feature-accentu-e", escapeName("2.0.0-feature-accentuée"))
    }

    @Test
    fun `Escaping slashes`() {
        assertEquals("feature-templating", escapeName("feature/templating"))
    }

}
