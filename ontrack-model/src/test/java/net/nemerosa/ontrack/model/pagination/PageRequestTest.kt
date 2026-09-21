package net.nemerosa.ontrack.model.pagination

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PageRequestTest {

    @Test
    fun `No previous page when starting from the origin`() {
        assertNull(PageRequest(0, 20).previous(40, 20))
    }

    @Test
    fun `Full previous page`() {
        assertNotNull(PageRequest(10, 10).previous(40, 10)) {
            assertEquals(0, it.offset)
            assertEquals(10, it.size)
        }
    }

    @Test
    fun `Offset previous page`() {
        assertNotNull(PageRequest(15, 10).previous(40, 10)) {
            assertEquals(5, it.offset)
            assertEquals(10, it.size)
        }
    }

    @Test
    fun `Reduced previous page`() {
        assertNotNull(PageRequest(7, 10).previous(40, 10)) {
            assertEquals(0, it.offset)
            assertEquals(7, it.size)
        }
    }

    @Test
    fun `No next page when being at the end`() {
        assertNull(PageRequest(20, 20).next(40, 20))
    }

    @Test
    fun `No next page when being after the end`() {
        assertNull(PageRequest(30, 20).next(40, 20))
    }

    @Test
    fun `No next page when being beyond the end`() {
        assertNull(PageRequest(40, 20).next(40, 20))
    }

    @Test
    fun `Full next page`() {
        assertNotNull(PageRequest(10, 10).next(40, 10)) {
            assertEquals(20, it.offset)
            assertEquals(10, it.size)
        }
    }

    @Test
    fun `Offset next page`() {
        assertNotNull(PageRequest(15, 10).next(40, 10)) {
            assertEquals(25, it.offset)
            assertEquals(10, it.size)
        }
    }

    @Test
    fun `Reduced next page`() {
        assertNotNull(PageRequest(26, 10).next(40, 10)) {
            assertEquals(36, it.offset)
            assertEquals(4, it.size)
        }
    }
}