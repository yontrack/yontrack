package net.nemerosa.ontrack.model.structure

import tools.jackson.core.JacksonException
import tools.jackson.databind.node.IntNode
import net.nemerosa.ontrack.model.structure.ID.Companion.isDefined
import net.nemerosa.ontrack.model.structure.ID.Companion.of
import net.nemerosa.ontrack.test.TestUtils
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class IDTest {
    @Test
    fun none() {
        val id = ID.NONE
        assertNotNull(id)
        assertFalse(id.isSet)
        assertEquals(0, id.value.toLong())
        assertEquals("0", id.toString())
    }

    @Test
    fun set() {
        val id = of(1)
        assertNotNull(id)
        assertTrue(id.isSet)
        assertEquals(1, id.value.toLong())
        assertEquals("1", id.toString())
    }

    @Test
    fun not_zero() {
        assertFailsWith<IllegalArgumentException> {
            of(0)
        }
    }

    @Test
    fun not_negative() {
        assertFailsWith<IllegalArgumentException> {
            of(-1)
        }
    }

    @Test
    fun set_to_json() {
        TestUtils.assertJsonWrite(
            IntNode(12),
            of(12)
        )
    }

    @Test
    @Throws(JacksonException::class)
    fun read_from_json() {
        TestUtils.assertJsonRead(
            of(9),
            IntNode(9),
            ID::class.java
        )
    }

    @Test
    @Throws(JacksonException::class)
    fun unset_to_json() {
        TestUtils.assertJsonWrite(
            IntNode(0),
            ID.NONE
        )
    }

    @Test
    fun is_defined_null() {
        assertFalse(isDefined(null))
    }

    @Test
    fun is_defined_none() {
        assertFalse(isDefined(ID.NONE))
    }

    @Test
    fun is_defined_set() {
        assertTrue(isDefined(of(1)))
    }
}