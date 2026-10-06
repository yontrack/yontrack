package net.nemerosa.ontrack.boot.search

import net.nemerosa.ontrack.boot.BRANCH_SEARCH_RESULT_TYPE
import net.nemerosa.ontrack.boot.BUILD_SEARCH_RESULT_TYPE
import net.nemerosa.ontrack.extension.general.BuildLinkSearchExtension
import net.nemerosa.ontrack.extension.general.ReleaseSearchExtension
import net.nemerosa.ontrack.model.security.Roles
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.NameDescription
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.SearchQueryRequest
import net.nemerosa.ontrack.model.structure.SearchResult
import net.nemerosa.ontrack.model.structure.SearchDocumentRenameReindexation
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import kotlin.test.assertEquals

/**
 * Search results after the rename of a project or a branch: their names are the current ones,
 * resolved when searching, and the documents matched on them are re-indexed after the rename.
 */
class SearchRenameIT : AbstractSearchTestSupport() {

    @Autowired
    private lateinit var searchDocumentRenameReindexation: SearchDocumentRenameReindexation

    /**
     * Random name, which no other one is similar to, even by trigram
     */
    private fun token() = "n" + UUID.randomUUID().toString().replace("-", "").take(15)

    private fun search(query: String, type: String) =
        searchService.search(SearchQueryRequest(query = query, types = listOf(type), size = 50))

    private fun renameProject(project: Project, name: String) {
        asAdmin {
            structureService.saveProject(
                Project(project.id, name, project.description, project.isDisabled, project.signature)
            )
        }
    }

    private fun renameBranch(branch: Branch, name: String) {
        asAdmin {
            structureService.saveBranch(
                Branch.of(branch.project, NameDescription.nd(name, branch.description ?: "")).withId(branch.id)
            )
        }
    }

    /**
     * Value at a path of the data of a result
     */
    private fun SearchResult.at(vararg path: String): Any? =
        path.fold<String, Any?>(data) { node, key -> (node as Map<*, *>?)?.get(key) }

    @Test
    fun `A renamed project shows its new name in the builds, releases, branches and build links, without any rebuild`() {
        val name = token()
        val version = token()
        val branch = project(token()).branch(token())
        val build = branch.build(name)
        build.release(version)
        val source = project(token()).branch(token()).build(token())
        source.linkTo(build)
        val oldName = branch.project.name
        val newName = token()
        renameProject(branch.project, newName)
        asUser {
            assertEquals(
                newName,
                search(name, BUILD_SEARCH_RESULT_TYPE).items.single().at("build", "branch", "project", "name")
            )
            assertEquals(
                newName,
                search(version, ReleaseSearchExtension.SEARCH_RESULT_TYPE).items.single()
                    .at("build", "branch", "project", "name")
            )
            assertEquals(
                newName,
                search(branch.name, BRANCH_SEARCH_RESULT_TYPE).items.single().at("branch", "project", "name")
            )
            // The link is not re-indexed before the commit of the rename, but shows the new name
            val link = search("$oldName:$name", BuildLinkSearchExtension.SEARCH_RESULT_TYPE).items.single()
            assertEquals(newName, link.at("targetBuild", "branch", "project", "name"))
            assertEquals(source.project.name, link.at("sourceBuild", "branch", "project", "name"))
        }
    }

    @Test
    fun `A renamed branch shows its new name in the builds and releases, without any rebuild`() {
        val name = token()
        val version = token()
        val branch = project(token()).branch(token())
        branch.build(name).release(version)
        val newName = token()
        renameBranch(branch, newName)
        asUser {
            assertEquals(newName, search(name, BUILD_SEARCH_RESULT_TYPE).items.single().at("build", "branch", "name"))
            assertEquals(
                newName,
                search(version, ReleaseSearchExtension.SEARCH_RESULT_TYPE).items.single().at("build", "branch", "name")
            )
        }
    }

    /**
     * The re-indexation runs after the commit of the rename: the entities must be committed.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `A renamed project finds its branches and the links to its builds by its new name only, once re-indexed`() {
        val name = token()
        val branch = project(token()).branch(token())
        val build = branch.build(name)
        val source = project(token()).branch(token()).build(token())
        source.linkTo(build)
        try {
            val oldName = branch.project.name
            val newName = token()
            renameProject(branch.project, newName)
            searchDocumentRenameReindexation.awaitCompletion()
            asUser {
                assertEquals(
                    listOf("$newName/${branch.name}"),
                    search(newName, BRANCH_SEARCH_RESULT_TYPE).items.map { it.title }
                )
                assertEquals(0, search(oldName, BRANCH_SEARCH_RESULT_TYPE).total)
                assertEquals(
                    listOf("$newName:$name"),
                    search("$newName:$name", BuildLinkSearchExtension.SEARCH_RESULT_TYPE).items.map { it.title }
                )
                assertEquals(0, search("$oldName:$name", BuildLinkSearchExtension.SEARCH_RESULT_TYPE).total)
            }
        } finally {
            asAdmin {
                structureService.deleteProject(source.project.id)
                structureService.deleteProject(branch.project.id)
            }
        }
    }

    @Test
    fun `The names of a project the user cannot see are not shown`() {
        val name = token()
        val target = project(token()).branch(token()).build(name)
        val source = project(token()).branch(token()).build(token())
        source.linkTo(target)
        withNoGrantViewToAll {
            source.asAccountWithProjectRole(Roles.PROJECT_READ_ONLY) {
                val link = search("${target.project.name}:$name", BuildLinkSearchExtension.SEARCH_RESULT_TYPE)
                    .items.single()
                assertEquals(source.project.name, link.at("sourceBuild", "branch", "project", "name"))
                assertEquals(source.branch.name, link.at("sourceBuild", "branch", "name"))
                assertEquals(null, link.at("targetBuild", "branch", "project", "name"))
                assertEquals(null, link.at("targetBuild", "branch", "name"))
                assertEquals(target.project.id(), link.at("targetBuild", "branch", "project", "id"))
            }
        }
    }

}
