package net.nemerosa.ontrack.extension.scm.catalog

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.nemerosa.ontrack.extension.scm.catalog.CatalogFixtures.entry
import net.nemerosa.ontrack.model.structure.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CatalogLinkServiceTest {

    private lateinit var catalogLinkService: CatalogLinkService
    private lateinit var scmCatalog: SCMCatalog
    private lateinit var scmCatalogProvider: SCMCatalogProvider
    private lateinit var structureService: StructureService
    private lateinit var entityStore: EntityStore

    private val project = Project(ID.of(1), "PRJ", "Project", false, Signature.of("test"))

    @BeforeEach
    fun setup() {
        scmCatalog = mockk(relaxed = true)

        scmCatalogProvider = mockk(relaxed = true)
        every { scmCatalogProvider.id } returns "test"

        structureService = mockk(relaxed = true)
        entityStore = mockk(relaxed = true)
        // No link by default (a relaxed mock cannot guess the generic type)
        every { entityStore.findByName(any(), STORE, EntityStore.DEFAULT_NAME, String::class) } returns null
        catalogLinkService = CatalogLinkServiceImpl(
            scmCatalog,
            listOf(scmCatalogProvider),
            structureService,
            entityStore
        )
    }

    @Test
    fun `Linked project`() {
        // List of projects
        every { structureService.projectList } returns listOf(project)
        // Catalog entries
        val entry = entry()
        every { scmCatalog.catalogEntries } returns sequenceOf(entry)
        // Matching
        every { scmCatalogProvider.matches(entry, project) } returns true
        // Collection of links
        catalogLinkService.computeCatalogLinks()
        // Checks that link is stored
        verify {
            entityStore.store(
                project,
                STORE,
                EntityStore.DEFAULT_NAME,
                entry.key
            )
        }
    }

    @Test
    fun `No link because project does not match entry`() {
        // List of projects
        every { structureService.projectList } returns listOf(project)
        // Catalog entries
        val entry = entry()
        every { scmCatalog.catalogEntries } returns sequenceOf(entry)
        // Matching
        every { scmCatalogProvider.matches(entry, project) } returns false
        // Collection of links
        catalogLinkService.computeCatalogLinks()
        // Checks that link is NOT stored
        verify(exactly = 0) {
            entityStore.store(
                project,
                STORE,
                EntityStore.DEFAULT_NAME,
                any<String>()
            )
        }
    }

    @Test
    fun `No link because provider not found`() {
        // List of projects
        every { structureService.projectList } returns listOf(project)
        // Catalog entries
        val entry = entry("unknown-provider")
        every { scmCatalog.catalogEntries } returns sequenceOf(entry)
        // Matching
        every { scmCatalogProvider.matches(entry, project) } returns true
        // Collection of links
        catalogLinkService.computeCatalogLinks()
        // Checks that link is NOT stored
        verify(exactly = 0) {
            entityStore.store(
                project,
                STORE,
                EntityStore.DEFAULT_NAME,
                any<String>()
            )
        }
    }

    @Test
    fun `Cleanup of left over keys`() {
        // List of projects
        every { structureService.projectList } returns listOf(project)
        // Catalog entries
        val entry = entry()
        every { scmCatalog.catalogEntries } returns sequenceOf(entry)
        // Matching
        every { scmCatalogProvider.matches(entry, project) } returns false
        // Existing key
        every { entityStore.findByName(project, STORE, EntityStore.DEFAULT_NAME, String::class) } returns entry.key
        // Collection of links
        catalogLinkService.computeCatalogLinks()
        // Asserts the link is deleted
        verify {
            entityStore.deleteByName(
                project,
                STORE,
                EntityStore.DEFAULT_NAME,
            )
        }
    }

    @Test
    fun `Cleanup of obsolete keys`() {
        // List of projects
        every { structureService.projectList } returns listOf(project)
        // Catalog entries
        val entry = entry()
        every { scmCatalog.catalogEntries } returns sequenceOf(entry)
        // Matching
        every { scmCatalogProvider.matches(entry, project) } returns false
        // Existing key
        every { entityStore.findByName(project, STORE, EntityStore.DEFAULT_NAME, String::class) } returns "unknown-key"
        // Collection of links
        catalogLinkService.computeCatalogLinks()
        // Asserts the link is deleted
        verify {
            entityStore.deleteByName(
                project,
                STORE,
                EntityStore.DEFAULT_NAME,
            )
        }
    }

    @Test
    fun `Getting a catalog entry from a project`() {
        val entry = entry()
        every { entityStore.findByName(project, STORE, EntityStore.DEFAULT_NAME, String::class) } returns entry.key
        every { scmCatalog.getCatalogEntry(entry.key) } returns entry
        val loaded = catalogLinkService.getSCMCatalogEntry(project)
        assertEquals(entry, loaded)
    }

    @Test
    fun `Catalog entry not found for a project`() {
        val entry = entry()
        every { entityStore.findByName(project, STORE, EntityStore.DEFAULT_NAME, String::class) } returns entry.key
        every { scmCatalog.getCatalogEntry(entry.key) } returns null
        val loaded = catalogLinkService.getSCMCatalogEntry(project)
        assertNull(loaded)
    }

    @Test
    fun `Getting linked project from an entry`() {
        val entry = entry()
        every {
            entityStore.findEntities(
                ProjectEntityType.PROJECT,
                STORE,
                EntityStoreFilter(
                    jsonFilter = "DATA = CAST(:key AS JSONB)",
                    jsonFilterCriterias = mapOf("key" to "\"${entry.key}\""),
                )
            )
        } returns listOf(ProjectEntityID(ProjectEntityType.PROJECT, project.id()))
        every { structureService.getProject(project.id) } returns project
        val loaded = catalogLinkService.getLinkedProject(entry)
        assertEquals(project, loaded)
    }

    @Test
    fun `Getting linked flag from an entry`() {
        val entry = entry()
        every {
            entityStore.findEntities(
                ProjectEntityType.PROJECT,
                STORE,
                EntityStoreFilter(
                    jsonFilter = "DATA = CAST(:key AS JSONB)",
                    jsonFilterCriterias = mapOf("key" to "\"${entry.key}\""),
                )
            )
        } returns listOf(ProjectEntityID(ProjectEntityType.PROJECT, project.id()))
        val linked = catalogLinkService.isLinked(entry)
        assertEquals(true, linked)
    }

    @Test
    fun `No linked project from an entry`() {
        val entry = entry()
        every { structureService.projectList } returns listOf(project)
        every {
            entityStore.findEntities(
                ProjectEntityType.PROJECT,
                STORE,
                any()
            )
        } returns emptyList()
        val loaded = catalogLinkService.getLinkedProject(entry)
        assertNull(loaded)
    }

    @Test
    fun `Orphan project`() {
        every { entityStore.findByName(project, STORE, EntityStore.DEFAULT_NAME, String::class) } returns null
        assertTrue(catalogLinkService.isOrphan(project))
        every { entityStore.findByName(project, STORE, EntityStore.DEFAULT_NAME, String::class) } returns "some-key"
        assertFalse(catalogLinkService.isOrphan(project))
    }

    companion object {
        private val STORE: String = CatalogLinkService::class.java.name
    }

}