package net.nemerosa.ontrack.boot.search

import net.nemerosa.ontrack.boot.BRANCH_SEARCH_RESULT_TYPE
import net.nemerosa.ontrack.boot.BUILD_SEARCH_RESULT_TYPE
import net.nemerosa.ontrack.model.security.Roles
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.NameDescription
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.SearchQueryRequest
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import kotlin.test.assertEquals

/**
 * Search documents for the branches, on Postgres.
 */
class BranchSearchIT : AbstractSearchTestSupport() {

    /**
     * Random name, which no other one is similar to, even by trigram
     */
    private fun token() = "b" + UUID.randomUUID().toString().replace("-", "").take(15)

    private fun search(query: String, types: List<String> = listOf(BRANCH_SEARCH_RESULT_TYPE)) =
        searchService.search(SearchQueryRequest(query = query, types = types, size = 50))

    @Test
    fun `A created branch is searchable in the transaction of its creation, with what its result renders`() {
        val name = token()
        val branch = doCreateBranch(project(token()), NameDescription.nd(name, "Some description"))
        val results = asUser { search(name).items }
        assertEquals(1, results.size)
        results.first().apply {
            assertEquals("${branch.project.name}/$name", title)
            assertEquals("Some description", description)
            assertEquals(BRANCH_SEARCH_RESULT_TYPE, type.id)
            @Suppress("UNCHECKED_CAST")
            val data = data?.get("branch") as Map<String, *>
            assertEquals(branch.id(), data["id"])
            assertEquals(name, data["name"])
            assertEquals("Some description", data["description"])
            assertEquals(false, data["disabled"])
            @Suppress("UNCHECKED_CAST")
            val project = data["project"] as Map<String, *>
            assertEquals(branch.project.id(), project["id"])
            assertEquals(branch.project.name, project["name"])
        }
    }

    @Test
    fun `A branch is found on the name of its project`() {
        val branch = project(token()).branch(token())
        val results = asUser { search(branch.project.name).items }
        assertEquals(listOf("${branch.project.name}/${branch.name}"), results.map { it.title })
    }

    @Test
    fun `A renamed project renames the titles of its branches`() {
        val newName = token()
        val branch = project(token()).branch(token())
        asAdmin {
            val project = branch.project
            structureService.saveProject(
                Project(project.id, newName, project.description, project.isDisabled, project.signature)
            )
        }
        val results = asUser { search(branch.name).items }
        assertEquals(listOf("$newName/${branch.name}"), results.map { it.title })
    }

    @Test
    fun `A renamed branch is searchable by its new name only`() {
        val oldName = token()
        val newName = token()
        val branch = project(token()).branch(oldName)
        asAdmin {
            structureService.saveBranch(
                Branch.of(branch.project, NameDescription.nd(newName, "")).withId(branch.id)
            )
        }
        assertEquals(0, asUser { search(oldName).total })
        assertEquals(listOf("${branch.project.name}/$newName"), asUser { search(newName).items.map { it.title } })
    }

    @Test
    fun `A deleted branch is not searchable any longer, nor are its builds`() {
        val name = token()
        val branch = project(token()).branch(name)
        branch.build("$name-1")
        branch.build("$name-2")
        val types = listOf(BRANCH_SEARCH_RESULT_TYPE, BUILD_SEARCH_RESULT_TYPE)
        assertEquals(3, asUser { search(name, types).total })
        asAdmin { structureService.deleteBranch(branch.id) }
        assertEquals(0, asUser { search(name, types).total })
    }

    @Test
    fun `Search branches and filter on access rights with correct totals`() {
        val prefix = token()
        val branches = (0..3).map {
            project(token()).branch(name = "$prefix-$it")
        }
        withNoGrantViewToAll {
            branches[0].project.asAccountWithProjectRole(Roles.PROJECT_READ_ONLY) {
                val results = search(prefix)
                assertEquals(1, results.total)
                assertEquals(listOf(branches[0].name), results.items.map { it.title.substringAfter("/") })
            }
            asGlobalRole(Roles.GLOBAL_READ_ONLY) {
                assertEquals(4, search(prefix).total)
            }
        }
    }

    /**
     * The rebuild runs in its own transactions: the branches must be committed.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `Rebuilding the branch documents`() {
        val prefix = token()
        val project = project(token()) {
            (1..3).forEach { branch("$prefix-$it") }
        }
        try {
            searchService.reindex(BRANCH_SEARCH_RESULT_TYPE)
            assertEquals(3, asUser { search(prefix).total })
        } finally {
            asAdmin { structureService.deleteProject(project.id) }
        }
    }

}
