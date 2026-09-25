package net.nemerosa.ontrack.extension.scm.catalog.search

import net.nemerosa.ontrack.extension.scm.catalog.CatalogFixtures
import net.nemerosa.ontrack.extension.scm.catalog.CatalogLinkService
import net.nemerosa.ontrack.extension.scm.catalog.SCMCatalog
import net.nemerosa.ontrack.extension.scm.catalog.SCMCatalogAccessFunction
import net.nemerosa.ontrack.extension.scm.catalog.mock.MockSCMCatalogProvider
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.security.Roles
import net.nemerosa.ontrack.model.structure.SearchDocument
import net.nemerosa.ontrack.model.structure.SearchDocumentService
import net.nemerosa.ontrack.model.structure.SearchQueryRequest
import net.nemerosa.ontrack.model.structure.SearchService
import net.nemerosa.ontrack.extension.scm.SCMExtensionConfigProperties
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Search documents for the SCM catalog entries, on Postgres: they belong to no project, and only
 * the users granted access to the SCM catalog see them.
 */
@AsAdminTest
class SCMCatalogSearchIndexerIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var scmCatalogProvider: MockSCMCatalogProvider

    @Autowired
    private lateinit var scmCatalog: SCMCatalog

    @Autowired
    private lateinit var catalogLinkService: CatalogLinkService

    @Autowired
    private lateinit var scmCatalogSearchIndexer: SCMCatalogSearchIndexer

    @Autowired
    private lateinit var searchDocumentService: SearchDocumentService

    @Autowired
    private lateinit var searchService: SearchService

    @Autowired
    private lateinit var scmExtensionConfigProperties: SCMExtensionConfigProperties

    private var catalogEnabled = false

    @BeforeEach
    fun enableCatalog() {
        catalogEnabled = scmExtensionConfigProperties.catalog.enabled
        scmExtensionConfigProperties.catalog.enabled = true
    }

    @AfterEach
    fun cleanup() {
        scmExtensionConfigProperties.catalog.enabled = catalogEnabled
        scmCatalogProvider.clear()
    }

    /**
     * Random name, which no other one is similar to, even by trigram
     */
    private fun token() = "r" + UUID.randomUUID().toString().replace("-", "").take(15)

    private fun search(query: String) =
        searchService.search(
            SearchQueryRequest(
                query = query,
                types = listOf(SCMCatalogSearchIndexer.SCM_CATALOG_SEARCH_RESULT_TYPE),
                size = 100,
            )
        )

    /**
     * Collects the catalog and writes the documents of all its entries, in the transaction of
     * the test.
     */
    private fun indexCatalog() {
        asAdmin {
            scmCatalog.collectSCMCatalog { }
            catalogLinkService.computeCatalogLinks()
            val documents = mutableListOf<SearchDocument>()
            scmCatalogSearchIndexer.indexAll { documents += it }
            searchDocumentService.index(documents)
        }
    }

    @Test
    fun `The catalog documents declare the SCM catalog access function`() {
        assertEquals(SCMCatalogAccessFunction::class.java, scmCatalogSearchIndexer.globalFunction)
    }

    @Test
    fun `A catalog entry linked to a project, with what its result renders`() {
        val repository = token()
        val entry = CatalogFixtures.entry(scm = "mocking", repository = "org/$repository", config = "config-1")
        val project = project()
        scmCatalogProvider.storeEntry(entry)
        scmCatalogProvider.linkEntry(entry, project)
        indexCatalog()

        val result = asGlobalRole(Roles.GLOBAL_READ_ONLY) { search(repository).items }.single()
        assertEquals("${project.name} (org/$repository)", result.title)
        @Suppress("UNCHECKED_CAST")
        val entryData = result.data?.get(SCMCatalogSearchIndexer.SEARCH_RESULT_SCM_CATALOG_ENTRY) as Map<String, *>
        assertEquals("org/$repository", entryData["repository"])
        assertEquals("uri:org/$repository", entryData["repositoryPage"])
        assertEquals("mocking", entryData["scm"])
        assertEquals("config-1", entryData["config"])
        @Suppress("UNCHECKED_CAST")
        val projectData = result.data?.get("project") as Map<String, *>
        assertEquals(project.id(), projectData["id"])
        assertEquals(project.name, projectData["name"])
    }

    @Test
    fun `A catalog entry is found on its full repository name`() {
        val repository = "org/" + token()
        scmCatalogProvider.storeEntry(CatalogFixtures.entry(scm = "mocking", repository = repository))
        indexCatalog()

        val result = asGlobalRole(Roles.GLOBAL_READ_ONLY) { search(repository).items }.single()
        assertEquals(repository, result.title)
        assertNull(result.data?.get("project"))
    }

    @Test
    fun `No catalog documents when the catalog is disabled`() {
        scmCatalogProvider.storeEntry(CatalogFixtures.entry(scm = "mocking", repository = "org/" + token()))
        asAdmin { scmCatalog.collectSCMCatalog { } }
        scmExtensionConfigProperties.catalog.enabled = false
        val documents = mutableListOf<SearchDocument>()
        asAdmin { scmCatalogSearchIndexer.indexAll { documents += it } }
        assertEquals(emptyList(), documents)
    }

    @Test
    fun `The catalog entries are visible only to the users granted access to the SCM catalog`() {
        val repository = token()
        scmCatalogProvider.storeEntry(CatalogFixtures.entry(scm = "mocking", repository = "org/$repository"))
        indexCatalog()
        val project = project()

        withNoGrantViewToAll {
            asGlobalRole(Roles.GLOBAL_READ_ONLY) {
                assertEquals(1, search(repository).total)
            }
            // No global role, no access to the catalog
            project.asAccountWithProjectRole(Roles.PROJECT_OWNER) {
                assertEquals(0, search(repository).total)
            }
        }
    }

}
