package net.nemerosa.ontrack.boot

import co.elastic.clients.elasticsearch._types.query_dsl.Query
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest
import io.mockk.mockk
import net.nemerosa.ontrack.model.structure.BuildDisplayNameService
import net.nemerosa.ontrack.model.structure.EXACT_MATCH_BOOST
import net.nemerosa.ontrack.model.structure.SearchIndexService
import net.nemerosa.ontrack.model.structure.StructureService
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The build index is an autocomplete one: it indexes the prefixes of the build names and display
 * names. A token matching a complete value therefore scores no better than a token matching a
 * prefix shared with other builds, unless the exact match is recognized and boosted on its own.
 */
class BuildSearchProviderTest {

    private val provider = BuildSearchProvider(
        structureService = mockk<StructureService>(),
        searchIndexService = mockk<SearchIndexService>(),
        buildDisplayNameService = mockk<BuildDisplayNameService>(),
    )

    @Test
    fun `Name and display name are indexed with an exact match sub-field`() {
        val request = provider.initIndex(CreateIndexRequest.Builder().index(BUILD_SEARCH_INDEX)).build()
        val properties = request.mappings()?.properties()
        assertNotNull(properties, "Mappings are defined")
        listOf("name", "displayName").forEach { field ->
            val text = properties[field]?.text()
            assertNotNull(text, "$field is mapped as text")
            val exact = text.fields()["exact"]
            assertNotNull(exact, "$field has an exact sub-field")
            assertTrue(exact.isKeyword, "The exact sub-field of $field is a keyword")
        }
    }

    @Test
    fun `Exact matches on the name and the display name are boosted`() {
        val query = provider.buildQuery(Query.Builder(), "some-token").build()
        val bool = query.bool()
        assertNotNull(bool, "The query is a boolean one")

        val exactMatches = bool.should().filter { it.isTerm }.map { it.term() }.associateBy { it.field() }
        assertEquals(
            setOf("name.exact", "displayName.exact"),
            exactMatches.keys,
            "Exact matches on the name and the display name"
        )
        exactMatches.values.forEach { term ->
            assertEquals("some-token", term.value().stringValue(), "Exact match on the whole token")
            assertEquals(true, term.caseInsensitive(), "Exact match is case insensitive")
            assertEquals(EXACT_MATCH_BOOST, term.boost(), "Exact match is boosted")
        }

        assertEquals(
            1,
            bool.should().count { it.isMultiMatch },
            "The regular multi match search is kept"
        )
    }
}
