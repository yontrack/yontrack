package net.nemerosa.ontrack.boot.search

import net.nemerosa.ontrack.boot.PROJECT_SEARCH_RESULT_TYPE
import net.nemerosa.ontrack.model.security.Roles
import net.nemerosa.ontrack.model.structure.NameDescription
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.SearchQueryRequest
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Search documents for the projects, on Postgres.
 */
class ProjectSearchIT : AbstractSearchTestSupport() {

    /**
     * Random name, which no other project is similar to, even by trigram
     */
    private fun token() = "p" + UUID.randomUUID().toString().replace("-", "").take(15)

    private fun searchProjects(query: String, size: Int = 20) =
        searchService.search(
            SearchQueryRequest(query = query, types = listOf(PROJECT_SEARCH_RESULT_TYPE), size = size)
        )

    @Test
    fun `A created project is searchable in the transaction of its creation`() {
        val candidate = project(NameDescription.nd(token(), "Some description"))
        // Same transaction as the creation, no refresh, no wait
        val results = asUser { searchProjects(candidate.name).items }
        assertTrue(results.isNotEmpty(), "At least one result")
        results[0].apply {
            assertEquals(candidate.name, title)
            assertEquals("Some description", description)
            assertEquals(PROJECT_SEARCH_RESULT_TYPE, type.id)
            @Suppress("UNCHECKED_CAST")
            val project = data?.get("project") as Map<String, *>
            assertEquals(candidate.id(), project["id"])
            assertEquals(candidate.name, project["name"])
        }
    }

    @Test
    fun `Searching for a project using the start of its name`() {
        val commonPart = token()
        val candidates = (1..6).map {
            project(name = NameDescription.nd("$commonPart-${token()}", ""))
        }
        val results = asUser { searchProjects(commonPart, size = 50) }
        assertEquals(6, results.total)
        assertEquals(
            candidates.map { it.name }.toSet(),
            results.items.map { it.title }.toSet()
        )
    }

    @Test
    fun `Searching for a project using its description`() {
        val word = token()
        val candidate = project(NameDescription.nd(token(), "The $word project"))
        val results = asUser { searchProjects(word).items }
        assertEquals(listOf(candidate.name), results.map { it.title })
    }

    @Test
    fun `A renamed project is searchable by its new name only`() {
        val oldName = token()
        val newName = token()
        val candidate = project(NameDescription.nd(oldName, ""))
        asAdmin {
            structureService.saveProject(
                Project(candidate.id, newName, candidate.description, candidate.isDisabled, candidate.signature)
            )
        }
        assertEquals(0, asUser { searchProjects(oldName).total })
        assertEquals(listOf(newName), asUser { searchProjects(newName).items.map { it.title } })
    }

    @Test
    fun `A deleted project is not searchable any longer`() {
        val candidate = project()
        asAdmin { structureService.deleteProject(candidate.id) }
        assertEquals(0, asUser { searchProjects(candidate.name).total })
    }

    @Test
    fun `Search projects and filter on access rights with correct totals`() {
        val prefix = token()
        val projects = (0..3).map {
            project(NameDescription(prefix + it, "Project $prefix #$it"))
        }
        withNoGrantViewToAll {
            projects[0].asAccountWithProjectRole(Roles.PROJECT_READ_ONLY) {
                val results = searchProjects(prefix)
                assertEquals(1, results.total)
                assertEquals(listOf(projects[0].name), results.items.map { it.title })
            }
            asGlobalRole(Roles.GLOBAL_READ_ONLY) {
                assertEquals(4, searchProjects(prefix).total)
            }
        }
    }

    @Test
    fun `The deprecated paginated search on projects is served by Postgres`() {
        val candidate = project()
        @Suppress("DEPRECATION")
        val results = asUser {
            searchService.paginatedSearch(
                net.nemerosa.ontrack.model.structure.SearchRequest(candidate.name, PROJECT_SEARCH_RESULT_TYPE)
            )
        }
        assertEquals(listOf(candidate.name), results.items.map { it.title })
    }

    /**
     * The rebuild runs in its own transactions: the projects must be committed.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `Rebuilding the project documents`() {
        val prefix = token()
        val projects = (1..3).map { project(NameDescription.nd("$prefix-$it", "")) }
        searchService.reindex(PROJECT_SEARCH_RESULT_TYPE)
        try {
            assertEquals(3, asUser { searchProjects(prefix).total })
        } finally {
            asAdmin { projects.forEach { structureService.deleteProject(it.id) } }
        }
    }

}
