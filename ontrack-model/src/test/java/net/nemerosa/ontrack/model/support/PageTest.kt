package net.nemerosa.ontrack.model.support

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PageTest {

    private val list = listOf(
            "Zero",
            "One",
            "Two",
            "Three",
            "Four",
            "Five",
            "Six",
            "Seven"
    )

    @Test
    fun `Sub list from 0`() {
        assertEquals(
                listOf(
                        "Zero",
                        "One",
                        "Two",
                        "Three"
                ),
                Page(0, 4).extract(list)
        )
    }

    @Test
    fun `Sub list from 0 to end`() {
        assertEquals(
                listOf(
                        "Zero",
                        "One",
                        "Two",
                        "Three",
                        "Four",
                        "Five",
                        "Six",
                        "Seven"
                ),
                Page(0, 8).extract(list)
        )
    }

    @Test
    fun `Sub list from 0 to beyond end`() {
        assertEquals(
                listOf(
                        "Zero",
                        "One",
                        "Two",
                        "Three",
                        "Four",
                        "Five",
                        "Six",
                        "Seven"
                ),
                Page(0, 12).extract(list)
        )
    }

    @Test
    fun `Sub list from middle`() {
        assertEquals(
                listOf(
                        "Four",
                        "Five"
                ),
                Page(4, 2).extract(list)
        )
    }

    @Test
    fun `Sub list from middle to end`() {
        assertEquals(
                listOf(
                        "Four",
                        "Five",
                        "Six",
                        "Seven"
                ),
                Page(4, 4).extract(list)
        )
    }

    @Test
    fun `Sub list from middle to beyond end`() {
        assertEquals(
                listOf(
                        "Four",
                        "Five",
                        "Six",
                        "Seven"
                ),
                Page(4, 8).extract(list)
        )
    }

    @Test
    fun `Sub list from beyond end`() {
        assertEquals(
                emptyList(),
                Page(8, 2).extract(list)
        )
    }

}
