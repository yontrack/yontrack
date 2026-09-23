package net.nemerosa.ontrack.boot.search

import kotlin.test.Test
import kotlin.test.assertEquals

class SearchIT : AbstractSearchTestSupport() {

    @Test
    fun `List of result types`() {
        val types = searchService.searchResultTypes
        val names = types.map { it.name }
        assertEquals(
            listOf(
                "Project", "Branch", "Build",
                "Build with Release", "Linked Build",
                "SCM Issue", "Git Branch", "SCM Commit",
                "Security finding",
            ),
            names
        )
    }

}