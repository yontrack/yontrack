package net.nemerosa.ontrack.boot.search

import kotlin.test.Test
import kotlin.test.assertEquals

class SearchIT : AbstractSearchTestSupport() {

    @Test
    fun `List of result types`() {
        // Without the test type
        val types = searchService.searchResultTypes.filter { it.id != TEST_ELASTIC_SEARCH_RESULT_TYPE }
        val names = types.map { it.name }
        assertEquals(
            listOf(
                "Project", "Branch", "Build",
                "Build with Release", "Linked Build",
                "SCM Issue", "Git Branch", "SCM Commit",
                "Security finding", "SCM Catalog",
            ),
            names
        )
    }

}