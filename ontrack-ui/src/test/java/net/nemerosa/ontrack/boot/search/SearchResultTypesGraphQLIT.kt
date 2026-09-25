package net.nemerosa.ontrack.boot.search

import net.nemerosa.ontrack.boot.support.UITest
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

@UITest
class SearchResultTypesGraphQLIT : AbstractQLKTITSupport() {

    @Test
    fun `Getting the list of search result types`() {
        val data = run("""{
            searchResultTypes {
                id
                name
                description
                feature { id }
            }
        }""")
        val types = data["searchResultTypes"]
        assertTrue(types.values().map { it["id"].asText() }.isNotEmpty())
    }

}