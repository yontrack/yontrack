package net.nemerosa.ontrack.extension.general

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LinkPropertyTypeTest {

    private val type: LinkPropertyType = LinkPropertyType(
            GeneralExtensionFeature()
    )

    @Test
    fun containsValue() {
        assertFalse(type.containsValue(LinkProperty.of("test", "http://wiki/one"), "two"))
        assertTrue(type.containsValue(LinkProperty.of("test", "http://wiki/one"), "one"))
        assertTrue(type.containsValue(LinkProperty.of("test", "http://wiki/one"), "ONE"))
        assertFalse(type.containsValue(LinkProperty.of("test", "http://wiki/one"), "test"))
        assertFalse(type.containsValue(LinkProperty.of("test", "http://wiki/one"), "name"))
        assertFalse(type.containsValue(LinkProperty.of("test", "http://wiki/one"), "value"))

        assertTrue(type.containsValue(LinkProperty.of("test1" to "http://wiki/one", "test2" to "http://wiki/two"), "one"))
        assertTrue(type.containsValue(LinkProperty.of("test1" to "http://wiki/one", "test2" to "http://wiki/two"), "two"))
        assertFalse(type.containsValue(LinkProperty.of("test1" to "http://wiki/one", "test2" to "http://wiki/two"), "three"))
    }

    @Test
    fun replacement() {
        val property = LinkProperty.of("test", "http://wiki/P1")
        assertEquals(
                LinkProperty.of("test", "http://wiki/P2"),
                type.replaceValue(property) { s -> s.replace("P1".toRegex(), "P2") }
        )
    }

}