package net.nemerosa.ontrack.boot.search

import net.nemerosa.ontrack.boot.BRANCH_SEARCH_INDEX
import net.nemerosa.ontrack.boot.BRANCH_SEARCH_RESULT_TYPE
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.SearchRequest
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Search indexation for branches
 */
class BranchSearchIT : AbstractSearchTestSupport() {

    private fun searchRequest(name: String) = SearchRequest(
        token = name,
        type = BRANCH_SEARCH_RESULT_TYPE,
    )

    @Test
    fun `Indexation of branches and looking for branches`() {
        val candidate = project<Branch> {
            branch()
        }
        // Creates 3 other projects
        repeat(3) { project { branch {} } }
        // Launching indexation for the branches
        index("branches")
        // Searches for the candidate project
        val results = asUser { searchService.paginatedSearch(searchRequest(candidate.name)).items }
        assertEquals(1, results.size)
        val result = results.first()
        result.apply {
            assertEquals(candidate.entityDisplayName, result.title)
            assertEquals(candidate.description, result.description)
        }
    }

    @Test
    fun `Finding projects and branches`() {
        // Creates a project
        val project = project()
        // Creates a branch with the same name than the project above
        val branch = project<Branch> {
            branch(name = project.name)
        }
        // Creates 3 other projects
        repeat(3) { project { branch {} } }
        // Launching indexation for the branches
        index("branches")
        // Searches for the name
        val results = asUser { searchService.paginatedSearch(searchRequest(branch.name)).items }
        assertEquals(1, results.size)
        results.first().apply {
            assertEquals(branch.entityDisplayName, title)
            assertEquals(branch.description, description)
        }
    }

    @Test
    fun `Search branch on project name`() {
        val branch = project<Branch> {
            branch {}
        }
        // Launching indexation for the branches
        index(BRANCH_SEARCH_INDEX)
        // Search on project name
        val results = asUser { searchService.paginatedSearch(searchRequest(branch.project.name)).items }
        // Branch is found
        assertNotNull(
            results.find { it.title == branch.entityDisplayName && it.type.id == BRANCH_SEARCH_RESULT_TYPE },
            "Branch found"
        )
    }

    @Test
    fun `Search branches and filter on access rights`() {
        val prefix = uid("P")
        // Creates projects and branches
        val branches = (0..3).map {
            project<Branch> {
                branch(name = "$prefix-$it")
            }
        }
        // Launching indexation for the branches
        index(BRANCH_SEARCH_INDEX)
        // Making sure to restrict access rights
        withNoGrantViewToAll {
            // Performing a search using the prefix and being authorised only for the first branch
            branches[0].asUserWithView {
                // Launching the search
                val results = searchService.paginatedSearch(searchRequest(prefix)).items
                // Names of branches
                val foundNames = results.map { it.title }
                // Checks that authorized branch is found
                assertTrue(branches[0].entityDisplayName in foundNames, "Authorized branch must be found")
                // Checks that unauthorized branches are NOT found
                (1..3).forEach {
                    assertTrue(
                        branches[it].entityDisplayName !in foundNames,
                        "Not authorized branches must be filtered out"
                    )
                }
            }
        }
    }

}