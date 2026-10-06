package net.nemerosa.ontrack.extension.git

import net.nemerosa.ontrack.extension.git.property.GitBranchConfigurationPropertyType
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.security.Roles
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.NameDescription
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.SearchQueryRequest
import net.nemerosa.ontrack.model.structure.SearchService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import java.util.UUID
import kotlin.test.assertEquals

/**
 * Search documents for the Git branches, on Postgres.
 */
class GitBranchSearchIndexerIT : AbstractGitTestSupport() {

    @Autowired
    private lateinit var searchService: SearchService

    /**
     * Random name, which no other one is similar to, even by trigram
     */
    private fun token() = "g" + UUID.randomUUID().toString().replace("-", "").take(15)

    private fun search(query: String) =
        searchService.search(
            SearchQueryRequest(query = query, types = listOf(GitBranchSearchIndexer.SEARCH_RESULT_TYPE), size = 50)
        )

    @Test
    fun `A Git branch is searchable as soon as it is assigned, with what its result renders`() {
        val gitBranch = "release/${token()}"
        val project = project()
        val branch = project.branch(token(), "Some description")
        branch.gitBranch(gitBranch)
        val results = asUser { search(gitBranch).items }
        assertEquals(1, results.size)
        results.first().apply {
            assertEquals(gitBranch, title)
            assertEquals(GitBranchSearchIndexer.SEARCH_RESULT_TYPE, type.id)
            assertEquals(gitBranch, data?.get("gitBranch"))
            @Suppress("UNCHECKED_CAST")
            val branchData = data?.get("branch") as Map<String, *>
            assertEquals(branch.id(), branchData["id"])
            assertEquals(branch.name, branchData["name"])
            assertEquals("Some description", branchData["description"])
            assertEquals(false, branchData["disabled"])
            @Suppress("UNCHECKED_CAST")
            val projectData = branchData["project"] as Map<String, *>
            assertEquals(project.id(), projectData["id"])
            assertEquals(project.name, projectData["name"])
        }
    }

    @Test
    fun `A Git branch is found on its prefix`() {
        val prefix = token()
        project().branch(token()).gitBranch("$prefix/1.0")
        assertEquals(listOf("$prefix/1.0"), asUser { search(prefix).items.map { it.title } })
    }

    @Test
    fun `A reassigned Git branch is searchable by its new name only`() {
        val oldGitBranch = token()
        val newGitBranch = token()
        val branch = project().branch(token())
        branch.gitBranch(oldGitBranch)
        branch.gitBranch(newGitBranch)
        assertEquals(0, asUser { search(oldGitBranch).total })
        assertEquals(listOf(newGitBranch), asUser { search(newGitBranch).items.map { it.title } })
    }

    @Test
    fun `An unassigned Git branch is not searchable any longer`() {
        val gitBranch = token()
        val branch = project().branch(token())
        branch.gitBranch(gitBranch)
        assertEquals(1, asUser { search(gitBranch).total })
        asAdmin { propertyService.deleteProperty(branch, GitBranchConfigurationPropertyType::class.java) }
        assertEquals(0, asUser { search(gitBranch).total })
    }

    @Test
    fun `The Git branch of a deleted branch is not searchable any longer`() {
        val gitBranch = token()
        val branch = project().branch(token())
        branch.gitBranch(gitBranch)
        assertEquals(1, asUser { search(gitBranch).total })
        asAdmin { structureService.deleteBranch(branch.id) }
        assertEquals(0, asUser { search(gitBranch).total })
    }

    @Test
    fun `The Git branch of an updated branch renders its new name, description and state`() {
        val gitBranch = token()
        val branch = project().branch(token())
        branch.gitBranch(gitBranch)
        val newName = token()
        asAdmin { structureService.saveBranch(branch.copy(name = newName, description = "New description")) }
        asAdmin { structureService.disableBranch(structureService.getBranch(branch.id)) }
        branchData(gitBranch).let { branchData ->
            assertEquals(newName, branchData["name"])
            assertEquals("New description", branchData["description"])
            assertEquals(true, branchData["disabled"])
        }
        asAdmin { structureService.enableBranch(structureService.getBranch(branch.id)) }
        assertEquals(false, branchData(gitBranch)["disabled"])
        // The update form of a branch changes its state as well
        asAdmin { structureService.saveBranch(structureService.getBranch(branch.id).withDisabled(true)) }
        assertEquals(true, branchData(gitBranch)["disabled"])
    }

    @Test
    fun `The Git branch of a branch of a renamed project renders its new name, without being rewritten`() {
        val gitBranch = token()
        val project = project()
        val branch = project.branch(token())
        branch.gitBranch(gitBranch)
        val oldName = project.name
        val newName = token()
        asAdmin { structureService.saveProject(project.copy(name = newName)) }
        @Suppress("UNCHECKED_CAST")
        assertEquals(
            newName,
            (branchData(gitBranch)["project"] as Map<String, *>)["name"],
            "The name is resolved when searching"
        )
        assertEquals(
            oldName,
            storedData(branch.id.toString()).path("branch").path("project").path("name").asText(),
            "The document is not rewritten by the update of the project"
        )
    }

    @Test
    fun `Search Git branches and filter on access rights`() {
        val prefix = token()
        val branches = (0..2).map { project().branch(token()).apply { gitBranch("$prefix-$it") } }
        withNoGrantViewToAll {
            branches[0].project.asAccountWithProjectRole(Roles.PROJECT_READ_ONLY) {
                val results = search(prefix)
                assertEquals(1, results.total)
                assertEquals(listOf("$prefix-0"), results.items.map { it.title })
            }
            asGlobalRole(Roles.GLOBAL_READ_ONLY) {
                assertEquals(3, search(prefix).total)
            }
        }
    }

    /**
     * The rebuild runs in its own transactions: the branches must be committed.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `Rebuilding the Git branch documents`() {
        val prefix = token()
        val project = project {
            branch(token()).gitBranch("$prefix-1")
            branch(token()).gitBranch("$prefix-2")
            branch(token())
        }
        try {
            searchService.reindex(GitBranchSearchIndexer.SEARCH_RESULT_TYPE)
            assertEquals(
                listOf("$prefix-1", "$prefix-2"),
                asUser { search(prefix).items.map { it.title }.sorted() }
            )
        } finally {
            asAdmin { structureService.deleteProject(project.id) }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun branchData(gitBranch: String) =
        asUser { search(gitBranch).items }.single().data?.get("branch") as Map<String, *>

    /**
     * Data of a Git branch document as stored, before the names are resolved
     */
    private fun storedData(key: String): JsonNode =
        namedParameterJdbcTemplate.queryForObject(
            "SELECT DATA::text FROM SEARCH_DOCUMENTS WHERE TYPE = :type AND KEY = :key",
            mapOf("type" to GitBranchSearchIndexer.SEARCH_RESULT_TYPE, "key" to key),
            String::class.java,
        )!!.parseAsJson()

    private fun Project.branch(name: String, description: String) =
        structureService.newBranch(Branch.of(this, NameDescription.nd(name, description)))

}
