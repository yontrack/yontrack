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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Names of the projects in the search documents, after a rename: resolved when searching, and
 * re-indexed where the documents are matched on them.
 *
 * The re-indexation is queued after the commit of the rename by the queue extension (#2083), absent
 * from this module: these tests call it directly.
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
        epsilon.renamedNames.clear()
        epsilon.onIndexRenamed = {}
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

    private fun rename(project: Project, name: String): Project =
        asAdmin {
            val renamed = Project(project.id, name, project.description, project.isDisabled, project.signature)
            structureService.saveProject(renamed)
            renamed
        }

    private fun reindex(project: Project) {
        searchDocumentRenameReindexation.reindexRenamed(ProjectEntityType.PROJECT, project.id)
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
    }

    @Test
    fun `Re-indexing a renamed project writes the documents referring to it, and deletes the stale ones`() {
        val u = token()
        val project = project()
        val other = project()
        asAdmin {
            searchDocumentService.index(epsilon.projectDocument("kept-$u", project, listOf(u)))
            searchDocumentService.index(epsilon.projectDocument("stale-$u", project, listOf(u)))
            searchDocumentService.index(epsilon.projectDocument("other-$u", other, listOf(u)))
        }
        val newName = token()
        val renamed = rename(project, newName)
        epsilon.source += epsilon.projectDocument("kept-$u", renamed, listOf(u))
        reindex(project)
        assertEquals(listOf(project.id()), epsilon.renamed)
        assertEquals(
            mapOf(
                "kept-$u" to "$newName kept-$u",
                "other-$u" to "${other.name} other-$u",
            ),
            search(u).items.associate { it.key to it.title },
        )
    }

    @Test
    fun `The re-indexation reads the current name of the project, not the one of the rename which triggered it`() {
        val project = project()
        rename(project, token())
        val last = token()
        rename(project, last)
        reindex(project)
        assertEquals(listOf(last), epsilon.renamedNames)
    }

    @Test
    fun `Nothing is re-indexed for a project which no longer exists`() {
        val project = project()
        asAdmin { structureService.deleteProject(project.id) }
        reindex(project)
        assertEquals(emptyList(), epsilon.renamed)
    }

    @Test
    fun `A failing re-indexation is counted, and not thrown`() {
        val project = project()
        val before = errors()
        rename(project, TestEpsilonSearchDocumentIndexer.FAILING + token())
        reindex(project)
        assertEquals(listOf(project.id()), epsilon.renamed)
        assertEquals(before + 1, errors())
    }

    /**
     * The re-indexations run on other threads, which see committed projects only.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `Two re-indexations of the same project run one at a time, whatever their replica`() {
        val project = project()
        try {
            rename(project, token())
            val started = AtomicInteger()
            val firstStarted = CountDownLatch(1)
            val release = CountDownLatch(1)
            epsilon.onIndexRenamed = { entity ->
                if (entity.id() == project.id() && started.incrementAndGet() == 1) {
                    firstStarted.countDown()
                    release.await(30, TimeUnit.SECONDS)
                }
            }
            // Two consumers, on two replicas, receiving a message for the same project
            val consumers = Executors.newFixedThreadPool(2)
            try {
                val first = consumers.submit { reindex(project) }
                assertTrue(firstStarted.await(30, TimeUnit.SECONDS), "The first re-indexation starts")
                val second = consumers.submit { reindex(project) }
                Thread.sleep(1_000)
                assertEquals(1, started.get(), "The second re-indexation waits for the first one")
                release.countDown()
                first.get(30, TimeUnit.SECONDS)
                second.get(30, TimeUnit.SECONDS)
                assertEquals(2, started.get(), "The second re-indexation runs once the first one is over")
            } finally {
                release.countDown()
                consumers.shutdown()
            }
        } finally {
            asAdmin { structureService.deleteProject(project.id) }
        }
    }

    /**
     * The re-indexations run on other threads, which see committed projects only.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `Re-indexations of two projects run side by side`() {
        val one = project()
        val two = project()
        try {
            rename(one, token())
            rename(two, token())
            val bothStarted = CountDownLatch(2)
            epsilon.onIndexRenamed = { entity ->
                if (entity.id() == one.id() || entity.id() == two.id()) {
                    bothStarted.countDown()
                    bothStarted.await(30, TimeUnit.SECONDS)
                }
            }
            val consumers = Executors.newFixedThreadPool(2)
            try {
                val first = consumers.submit { reindex(one) }
                val second = consumers.submit { reindex(two) }
                first.get(30, TimeUnit.SECONDS)
                second.get(30, TimeUnit.SECONDS)
                assertEquals(0, bothStarted.count, "Both re-indexations ran at the same time")
            } finally {
                consumers.shutdown()
            }
        } finally {
            asAdmin {
                structureService.deleteProject(one.id)
                structureService.deleteProject(two.id)
            }
        }
    }

}
