package net.nemerosa.ontrack.model.pagination

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PaginatedListTest {

    @Test
    fun `Paginating an empty list`() {
        val page = PaginatedList.create(
                emptyList<Int>(),
                0,
                10
        )
        assertEquals(0, page.pageInfo.currentOffset)
        assertEquals(0, page.pageInfo.currentSize)
        assertEquals(0, page.pageInfo.totalSize)
        assertEquals(0, page.pageInfo.pageIndex)
        assertEquals(0, page.pageInfo.pageTotal)
        assertNull(page.pageInfo.previousPage)
        assertNull(page.pageInfo.nextPage)
        assertEquals(
                emptyList(),
                page.pageItems
        )
    }

    @Test
    fun `Paginating more than the list`() {
        val page = PaginatedList.create(
                (0..4).toList(),
                0,
                10
        )
        assertEquals(0, page.pageInfo.currentOffset)
        assertEquals(5, page.pageInfo.currentSize)
        assertEquals(5, page.pageInfo.totalSize)
        assertEquals(0, page.pageInfo.pageIndex)
        assertEquals(1, page.pageInfo.pageTotal)
        assertNull(page.pageInfo.previousPage)
        assertNull(page.pageInfo.nextPage)
        assertEquals(
                (0..4).toList(),
                page.pageItems
        )
    }

    @Test
    fun `Paginating a list from start`() {
        val page = PaginatedList.create(
                (0..19).toList(),
                0,
                10
        )
        assertEquals(0, page.pageInfo.currentOffset)
        assertEquals(10, page.pageInfo.currentSize)
        assertEquals(20, page.pageInfo.totalSize)
        assertEquals(0, page.pageInfo.pageIndex)
        assertEquals(2, page.pageInfo.pageTotal)
        assertNull(page.pageInfo.previousPage)
        assertNotNull(page.pageInfo.nextPage) {
            assertEquals(10, it.offset)
            assertEquals(10, it.size)
        }
        assertEquals(
                (0..9).toList(),
                page.pageItems
        )
    }

}