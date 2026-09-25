package net.nemerosa.ontrack.repository.search

import net.nemerosa.ontrack.model.structure.SearchHighlightPart
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SearchHeadlineTest {

    private val start = SearchHeadline.START
    private val stop = SearchHeadline.STOP
    private val lt = SearchHeadline.LT

    @Test
    fun `A headline without any match is one plain part`() {
        assertEquals(
            listOf(SearchHighlightPart("Some text", match = false)),
            SearchHeadline.parse("Some text")
        )
    }

    @Test
    fun `The matches of a headline are flagged`() {
        assertEquals(
            listOf(
                SearchHighlightPart("Fixes ", match = false),
                SearchHighlightPart("parser", match = true),
                SearchHighlightPart(" and ", match = false),
                SearchHighlightPart("lexer", match = true),
            ),
            SearchHeadline.parse("Fixes ${start}parser$stop and ${start}lexer$stop")
        )
    }

    @Test
    fun `Adjacent matches are kept apart`() {
        assertEquals(
            listOf(
                SearchHighlightPart("one", match = true),
                SearchHighlightPart(" ", match = false),
                SearchHighlightPart("two", match = true),
            ),
            SearchHeadline.parse("${start}one$stop ${start}two$stop")
        )
    }

    @Test
    fun `Markup in the text stays text`() {
        assertEquals(
            listOf(
                SearchHighlightPart("<b>", match = false),
                SearchHighlightPart("x", match = true),
                SearchHighlightPart("</b>", match = false),
            ),
            SearchHeadline.parse("${lt}b>${start}x$stop$lt/b>")
        )
    }

    @Test
    fun `An empty headline has no part`() {
        assertEquals(emptyList(), SearchHeadline.parse(""))
    }

}
