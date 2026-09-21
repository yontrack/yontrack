package net.nemerosa.ontrack.extension.general

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class MetaInfoPropertyTest {

    @Test
    fun match_nok_no_item() {
        assertFalse(
                MetaInfoProperty(
                        emptyList()
                ).matchNameValue("name", "value")
        )
    }

    @Test
    fun match_name_nok() {
        assertFalse(
                MetaInfoProperty(
                        listOf(MetaInfoPropertyItem.of("name", "value"))
                ).matchNameValue("nam", "value")
        )
    }

    @Test
    fun match_value_nok_with_exact_value() {
        assertFalse(
                MetaInfoProperty(
                        listOf(MetaInfoPropertyItem.of("name", "value"))
                ).matchNameValue("name", "val")
        )
    }

    @Test
    fun match_value_ok_with_exact_value() {
        assertTrue(
                MetaInfoProperty(
                        listOf(MetaInfoPropertyItem.of("name", "value"))
                ).matchNameValue("name", "value")
        )
    }

    @Test
    fun match_value_ok_with_pattern() {
        assertTrue(
                MetaInfoProperty(
                        listOf(MetaInfoPropertyItem.of("name", "value"))
                ).matchNameValue("name", "val*")
        )
    }

    @Test
    fun match_value_ok_with_wildcard() {
        assertTrue(
                MetaInfoProperty(
                        listOf(MetaInfoPropertyItem.of("name", "value"))
                ).matchNameValue("name", "*")
        )
    }

    @Test
    fun match_value_ok_with_blank() {
        assertTrue(
                MetaInfoProperty(
                        listOf(MetaInfoPropertyItem.of("name", "value"))
                ).matchNameValue("name", "")
        )
    }
}
