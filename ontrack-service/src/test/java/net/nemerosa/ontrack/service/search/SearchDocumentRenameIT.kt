package net.nemerosa.ontrack.service.search

import io.micrometer.core.instrument.MeterRegistry
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.structure.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Names of the projects in the search documents, after a rename: resolved when searching, and
 * re-indexed after the commit of the rename where the documents are matched on them.
 */
@AsAdminTest
class SearchDocumentRenameIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var searchService: SearchService

    @Autowired
    private lateinit var searchDocumentService: SearchDocumentService

    @Autowired
    private lateinit var searchDocumentRenameReindexation: SearchDocumentRenameReindexation

    @Autowired
    private lateinit var epsilon: TestEpsilonSearchDocumentIndexer

    @Autowired
    private lateinit var meterRegistry: MeterRegistry

    @AfterEach
    fun cleanup() {
        epsilon.source.clear()
        epsilon.renamed.clear()
    }

    /**
     * Random token, which no other document is similar to, even by trigram
     */
    private fun token() = "r" + UUID.randomUUID().toString().replace("-", "").take(15)

    private fun search(query: String) = asAdmin {
        searchService.search(
            SearchQueryRequest(query = query, types = listOf(TestEpsilonSearchDocumentIndexer.TYPE), size = 20)
        )
    }

    private val SearchResult.key: String get() = (data?.get("key") as String?) ?: ""

    @Suppress("UNCHECKED_CAST")
    private val SearchResult.projectName: Any? get() = (data?.get("project") as Map<String, *>)["name"]

    private fun rename(project: Project, name: String) {
        asAdmin {
            structureService.saveProject(
                Project(project.id, name, project.description, project.isDisabled, project.signature)
            )
        }
    }

    private fun errors() =
        meterRegistry.find("ontrack_search_index_errors").tag("type", TestEpsilonSearchDocumentIndexer.TYPE)
            .counter()?.count() ?: 0.0

    @Test
    fun `The name of a renamed project is resolved when searching, before any re-indexation`() {
        val u = token()
        val project = project()
        asAdmin { searchDocumentService.index(epsilon.projectDocument("a-$u", project, listOf(u))) }
        val newName = token()
        rename(project, newName)
        val result = search(u).items.single()
        assertEquals("${project.name} a-$u", result.title, "The document is not rewritten")
        assertEquals(newName, result.projectName, "The name of the project is the current one")
        assertFalse(project.id() in epsilon.renamed, "Nothing is re-indexed before the commit of the rename")
    }

    /**
     * The re-indexation runs after the commit of the rename: the project must be committed.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `Renaming a project re-indexes the documents referring to it after the commit, and deletes the stale ones`() {
        val u = token()
        val project = project()
        val other = project()
        try {
            asAdmin {
                searchDocumentService.index(epsilon.projectDocument("kept-$u", project, listOf(u)))
                searchDocumentService.index(epsilon.projectDocument("stale-$u", project, listOf(u)))
                searchDocumentService.index(epsilon.projectDocument("other-$u", other, listOf(u)))
            }
            val newName = token()
            val renamed = Project(project.id, newName, project.description, project.isDisabled, project.signature)
            epsilon.source += epsilon.projectDocument("kept-$u", renamed, listOf(u))
            rename(project, newName)
            searchDocumentRenameReindexation.awaitCompletion()
            assertEquals(listOf(project.id()), epsilon.renamed)
            assertEquals(
                mapOf(
                    "kept-$u" to "$newName kept-$u",
                    "other-$u" to "${other.name} other-$u",
                ),
                search(u).items.associate { it.key to it.title },
            )
        } finally {
            asAdmin {
                structureService.deleteProject(project.id)
                structureService.deleteProject(other.id)
            }
        }
    }

    /**
     * The re-indexation runs after the commit of the rename: the project must be committed.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `A project update which does not rename it triggers no re-indexation`() {
        val project = project()
        try {
            asAdmin {
                structureService.saveProject(
                    Project(project.id, project.name, "New description", project.isDisabled, project.signature)
                )
                structureService.disableProject(structureService.getProject(project.id))
            }
            searchDocumentRenameReindexation.awaitCompletion()
            assertFalse(project.id() in epsilon.renamed, "No re-indexation without a rename")
            // Checking the rename does trigger it
            rename(asAdmin { structureService.getProject(project.id) }, token())
            searchDocumentRenameReindexation.awaitCompletion()
            assertTrue(project.id() in epsilon.renamed, "Re-indexation on a rename")
        } finally {
            asAdmin { structureService.deleteProject(project.id) }
        }
    }

    /**
     * The re-indexation runs after the commit of the rename: the project must be committed.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `A failing re-indexation leaves the rename committed and is counted`() {
        val project = project()
        try {
            val before = errors()
            val newName = TestEpsilonSearchDocumentIndexer.FAILING + token()
            rename(project, newName)
            searchDocumentRenameReindexation.awaitCompletion()
            assertEquals(listOf(project.id()), epsilon.renamed)
            assertEquals(newName, asAdmin { structureService.getProject(project.id).name })
            assertEquals(before + 1, errors())
        } finally {
            asAdmin { structureService.deleteProject(project.id) }
        }
    }

}
