package net.nemerosa.ontrack.repository.search

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ParsedSearchQueryTest {

    @Test
    fun `A query shorter than two characters is not searched`() {
        assertNull(ParsedSearchQuery.parse(""))
        assertNull(ParsedSearchQuery.parse("a"))
        assertNull(ParsedSearchQuery.parse("   a  "))
    }

    @Test
    fun `A query of two characters matches on exact and prefix only`() {
        val parsed = assertNotNull(ParsedSearchQuery.parse("ab"))
        assertEquals(
            listOf(SearchMatchTier.EXACT, SearchMatchTier.PREFIX),
            parsed.tiers
        )
    }

    @Test
    fun `A query of three characters or more matches on every tier`() {
        val parsed = assertNotNull(ParsedSearchQuery.parse("abc"))
        assertEquals(
            listOf(SearchMatchTier.EXACT, SearchMatchTier.PREFIX, SearchMatchTier.FULL_TEXT, SearchMatchTier.TRIGRAM),
            parsed.tiers
        )
    }

    @Test
    fun `The query is trimmed, lower-cased and its white spaces collapsed`() {
        val parsed = assertNotNull(ParsedSearchQuery.parse("  My   Project\t1 "))
        assertEquals("my project 1", parsed.text)
    }

    @Test
    fun `Exact and prefix patterns work on one identifier per line`() {
        val parsed = assertNotNull(ParsedSearchQuery.parse("Release/4.1"))
        assertEquals("%\nrelease/4.1\n%", parsed.exactPattern)
        assertEquals("%\nrelease/4.1%", parsed.identifierPrefixPattern)
        assertEquals("release/4.1%", parsed.titlePrefixPattern)
    }

    @Test
    fun `LIKE wildcards in the query are matched literally`() {
        val parsed = assertNotNull(ParsedSearchQuery.parse("50%_a\\b"))
        assertEquals("50\\%\\_a\\\\b%", parsed.titlePrefixPattern)
    }

    @Test
    fun `Full-text query is a conjunction of quoted prefixes`() {
        val parsed = assertNotNull(ParsedSearchQuery.parse("billing Release/4.1"))
        assertEquals("'billing':* & 'release/4.1':*", parsed.tsQuery)
    }

    @Test
    fun `Quotes and backslashes are escaped in the full-text query`() {
        val parsed = assertNotNull(ParsedSearchQuery.parse("it's a\\b"))
        assertEquals("'it''s':* & 'a\\\\b':*", parsed.tsQuery)
    }

    @Test
    fun `Identifiers are lower-cased, one per line, with a new line around them`() {
        assertEquals(
            "\nmy-project\nv1.0\n",
            SearchDocumentIdentifiers.encode(listOf("My-Project", "V1.0"))
        )
    }

    @Test
    fun `Blank identifiers are dropped and line breaks inside an identifier are replaced`() {
        assertEquals(
            "\na b\n",
            SearchDocumentIdentifiers.encode(listOf("", "  ", "A\nB"))
        )
    }

    @Test
    fun `No identifier at all`() {
        assertEquals("\n", SearchDocumentIdentifiers.encode(emptyList()))
    }

}
