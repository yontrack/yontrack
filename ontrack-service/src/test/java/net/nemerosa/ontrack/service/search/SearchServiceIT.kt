package net.nemerosa.ontrack.service.search

import io.micrometer.core.instrument.MeterRegistry
import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.security.ProjectEdit
import net.nemerosa.ontrack.model.security.Roles
import net.nemerosa.ontrack.model.structure.*
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@AsAdminTest
class SearchServiceIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var searchService: SearchService

    @Autowired
    private lateinit var searchDocumentService: SearchDocumentService

    @Autowired
    private lateinit var alpha: TestAlphaSearchDocumentIndexer

    @Autowired
    private lateinit var beta: TestBetaSearchDocumentIndexer

    @Autowired
    private lateinit var gamma: TestGammaSearchDocumentIndexer

    @Autowired
    private lateinit var delta: TestDeltaSearchDocumentIndexer

    @Autowired
    private lateinit var meterRegistry: MeterRegistry

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private val testTypes = listOf(TestAlphaSearchDocumentIndexer.TYPE, TestBetaSearchDocumentIndexer.TYPE)

    @AfterEach
    fun cleanup() {
        alpha.source.clear()
        alpha.blocker = null
    }

    private fun index(vararg documents: SearchDocument) {
        asAdmin {
            documents.forEach { searchDocumentService.index(it) }
        }
    }

    private fun search(
        query: String,
        types: List<String> = testTypes,
        offset: Int = 0,
        size: Int = 20,
        perType: Int? = null,
    ) = searchService.search(
        SearchQueryRequest(query = query, types = types, offset = offset, size = size, perType = perType)
    )

    /**
     * Random token, which no other document is similar to, even by trigram
     */
    private fun token() = "t" + UUID.randomUUID().toString().replace("-", "").take(15)

    private val SearchResult.key: String get() = (data?.get("key") as String?) ?: ""

    @Test
    fun `The types of the indexers are searchable`() {
        val ids = searchService.searchResultTypes.map { it.id }
        assertTrue(TestAlphaSearchDocumentIndexer.TYPE in ids)
        assertTrue(TestBetaSearchDocumentIndexer.TYPE in ids)
    }

    @Test
    fun `A query shorter than two characters returns nothing`() {
        val project = project()
        index(alpha.document(uid("k"), "x", project, identifiers = listOf("x")))
        val results = asAdmin { search("x") }
        assertEquals(0, results.total)
        assertTrue(results.items.isEmpty())
    }

    @Test
    fun `Exact beats prefix beats full-text beats trigram`() {
        val project = project()
        val u = token()
        val typo = u.dropLast(1) + (if (u.last() == '0') '1' else '0')
        index(
            alpha.document("trigram-$u", typo, project, identifiers = listOf(typo)),
            alpha.document("fulltext-$u", "Some title", project, text = "About ${u}zz and more"),
            alpha.document("prefix-$u", "Other title", project, identifiers = listOf("$u-suffix")),
            alpha.document("exact-$u", "Exact", project, identifiers = listOf(u.uppercase())),
        )
        val results = asAdmin { search(u) }
        assertEquals(
            listOf("exact-$u", "prefix-$u", "fulltext-$u", "trigram-$u"),
            results.items.map { it.key }.filter { it.endsWith(u) }
        )
    }

    @Test
    fun `A prefix of the title matches`() {
        val project = project()
        val u = token()
        index(alpha.document("title-$u", "${u}Title", project))
        val results = asAdmin { search(u.uppercase()) }
        assertEquals(listOf("title-$u"), results.items.map { it.key })
        assertEquals("${u}Title", results.items.first().title)
    }

    @Test
    fun `A two-character query matches exact identifiers`() {
        val project = project()
        val u = token()
        // Two characters, unique enough in the scope of this test's project
        index(alpha.document("two-$u", "Title $u", project, identifiers = listOf("q9", u)))
        val results = asAdmin { search("q9") }
        assertTrue(results.items.any { it.key == "two-$u" })
    }

    @Test
    fun `Ties are broken by recency, newest first`() {
        val project = project()
        val u = token()
        val now = Time.now()
        index(
            alpha.document("old-$u", "Old", project, identifiers = listOf(u), updatedAt = now.minusDays(2)),
            alpha.document("new-$u", "New", project, identifiers = listOf(u), updatedAt = now.minusDays(1)),
        )
        val results = asAdmin { search(u) }
        assertEquals(listOf("new-$u", "old-$u"), results.items.map { it.key })
    }

    @Test
    fun `No type precedence - an exact match of a later type ranks before a prefix match of an earlier type`() {
        val project = project()
        val u = token()
        val time = Time.now().minusDays(1)
        index(
            alpha.document("alpha-prefix-$u", "A", project, identifiers = listOf("$u-more"), updatedAt = time),
            beta.document("beta-exact-$u", "B", project, identifiers = listOf(u), updatedAt = time),
        )
        val results = asAdmin { search(u) }
        assertEquals(listOf("beta-exact-$u", "alpha-prefix-$u"), results.items.map { it.key })
    }

    @Test
    fun `The order of the types breaks the ties last`() {
        val project = project()
        val u = token()
        val time = Time.now().minusDays(1)
        index(
            beta.document("beta-$u", "B", project, identifiers = listOf(u), updatedAt = time),
            alpha.document("alpha-$u", "A", project, identifiers = listOf(u), updatedAt = time),
        )
        val results = asAdmin { search(u) }
        assertEquals(listOf("alpha-$u", "beta-$u"), results.items.map { it.key })
    }

    @Test
    fun `Facets and total`() {
        val project = project()
        val u = token()
        index(*(1..5).map { alpha.document("a$it-$u", "A $it", project, identifiers = listOf("$u-$it")) }.toTypedArray())
        index(*(1..4).map { beta.document("b$it-$u", "B $it", project, identifiers = listOf("$u-$it")) }.toTypedArray())
        val results = asAdmin { search(u, size = 3) }
        assertEquals(9, results.total)
        assertEquals(3, results.items.size)
        assertEquals(
            mapOf(TestAlphaSearchDocumentIndexer.TYPE to 5, TestBetaSearchDocumentIndexer.TYPE to 4),
            results.facets.associate { it.type.id to it.count }
        )
    }

    @Test
    fun `Restricting the search to some types`() {
        val project = project()
        val u = token()
        index(
            alpha.document("a-$u", "A", project, identifiers = listOf(u)),
            beta.document("b-$u", "B", project, identifiers = listOf(u)),
        )
        val results = asAdmin { search(u, types = listOf(TestBetaSearchDocumentIndexer.TYPE)) }
        assertEquals(1, results.total)
        assertEquals(listOf("b-$u"), results.items.map { it.key })
        assertEquals(listOf(TestBetaSearchDocumentIndexer.TYPE), results.facets.map { it.type.id })
    }

    @Test
    fun `Pagination`() {
        val project = project()
        val u = token()
        val now = Time.now()
        index(*(1..5).map {
            alpha.document("p$it-$u", "P $it", project, identifiers = listOf(u), updatedAt = now.minusMinutes(it.toLong()))
        }.toTypedArray())
        val results = asAdmin { search(u, offset = 2, size = 2) }
        assertEquals(5, results.total)
        assertEquals(listOf("p3-$u", "p4-$u"), results.items.map { it.key })
    }

    @Test
    fun `Best results per type`() {
        val project = project()
        val u = token()
        val now = Time.now()
        index(*(1..5).map {
            alpha.document("a$it-$u", "A $it", project, identifiers = listOf(u), updatedAt = now.minusMinutes(it.toLong()))
        }.toTypedArray())
        index(*(1..4).map {
            beta.document("b$it-$u", "B $it", project, identifiers = listOf(u), updatedAt = now.minusMinutes(10L + it))
        }.toTypedArray())
        val results = asAdmin { search(u, perType = 2) }
        assertEquals(9, results.total)
        assertEquals(listOf("a1-$u", "a2-$u", "b1-$u", "b2-$u"), results.items.map { it.key })
    }

    @Test
    fun `Access is filtered with correct totals and facets for a restricted user`() {
        val u = token()
        val visible = project()
        val hidden = project()
        index(*(1..3).map { alpha.document("v$it-$u", "V $it", visible, identifiers = listOf("$u-$it")) }.toTypedArray())
        index(*(1..4).map { alpha.document("h$it-$u", "H $it", hidden, identifiers = listOf("$u-$it")) }.toTypedArray())
        withNoGrantViewToAll {
            visible.asAccountWithProjectRole(Roles.PROJECT_READ_ONLY) {
                val results = search(u, size = 2)
                assertEquals(3, results.total)
                assertEquals(2, results.items.size)
                assertEquals(mapOf(TestAlphaSearchDocumentIndexer.TYPE to 3), results.facets.associate { it.type.id to it.count })
                assertTrue(results.items.all { it.key.startsWith("v") })
            }
            asGlobalRole(Roles.GLOBAL_READ_ONLY) {
                assertEquals(7, search(u).total)
            }
        }
    }

    @Test
    fun `Documents without a project need the global function of their type`() {
        val u = token()
        val project = project()
        index(
            beta.document("global-$u", "Global", project = null, identifiers = listOf(u)),
            alpha.document("orphan-$u", "Orphan", project = null, identifiers = listOf(u)),
            beta.document("project-$u", "Project", project, identifiers = listOf(u)),
        )
        withNoGrantViewToAll {
            asGlobalRole(Roles.GLOBAL_ADMINISTRATOR) {
                // The alpha type declares no global function: its project-less documents are never shown
                assertEquals(listOf("global-$u", "project-$u"), search(u).items.map { it.key }.sorted())
            }
            asGlobalRole(Roles.GLOBAL_READ_ONLY) {
                assertEquals(listOf("project-$u"), search(u).items.map { it.key })
            }
        }
    }

    @Test
    fun `The documents of a type with a project function are visible only where it is granted`() {
        val u = token()
        val granted = project()
        val notGranted = project()
        index(
            gamma.document("granted-$u", "Granted", granted, identifiers = listOf(u)),
            gamma.document("not-granted-$u", "Not granted", notGranted, identifiers = listOf(u)),
            alpha.document("alpha-$u", "Alpha", notGranted, identifiers = listOf(u)),
        )
        val types = listOf(TestAlphaSearchDocumentIndexer.TYPE, TestGammaSearchDocumentIndexer.TYPE)
        withNoGrantViewToAll {
            // Project view on both projects, the function of the type on one only
            asUser()
                .withView(granted).withProjectFunction(granted, ProjectEdit::class.java)
                .withView(notGranted)
                .call {
                    val results = search(u, types = types)
                    assertEquals(listOf("alpha-$u", "granted-$u"), results.items.map { it.key }.sorted())
                    assertEquals(2, results.total)
                    assertEquals(
                        mapOf(TestAlphaSearchDocumentIndexer.TYPE to 1, TestGammaSearchDocumentIndexer.TYPE to 1),
                        results.facets.associate { it.type.id to it.count }
                    )
                }
            // A project role without the function: the project documents of the other types only
            notGranted.asAccountWithProjectRole(Roles.PROJECT_READ_ONLY) {
                assertEquals(listOf("alpha-$u"), search(u, types = types).items.map { it.key })
            }
            // A project role with the function
            granted.asAccountWithProjectRole(Roles.PROJECT_OWNER) {
                assertEquals(listOf("granted-$u"), search(u, types = types).items.map { it.key })
            }
            // A global role with the project view, without the function
            asGlobalRole(Roles.GLOBAL_READ_ONLY) {
                assertEquals(listOf("alpha-$u"), search(u, types = types).items.map { it.key })
            }
            // A global role with the function
            asGlobalRole(Roles.GLOBAL_ADMINISTRATOR) {
                assertEquals(
                    listOf("alpha-$u", "granted-$u", "not-granted-$u"),
                    search(u, types = types).items.map { it.key }.sorted()
                )
            }
        }
    }

    @Test
    fun `The documents of a type without fuzzy matching are not matched by similarity`() {
        val project = project()
        val u = token()
        val typo = u.dropLast(1) + (if (u.last() == '0') '1' else '0')
        index(
            delta.document("delta-exact-$u", "Exact", project, identifiers = listOf(u)),
            delta.document("delta-similar-$u", typo, project, identifiers = listOf(typo)),
            alpha.document("alpha-similar-$u", typo, project, identifiers = listOf(typo)),
        )
        val results = asAdmin {
            search(u, types = listOf(TestAlphaSearchDocumentIndexer.TYPE, TestDeltaSearchDocumentIndexer.TYPE))
        }
        assertEquals(listOf("delta-exact-$u", "alpha-similar-$u"), results.items.map { it.key })
    }

    @Test
    fun `Documents are written in the transaction of the caller`() {
        val u = token()
        val found = asAdmin {
            TransactionTemplate(transactionManager).execute {
                val project = structureService.newProject(Project.of(NameDescription.nd(uid("P"), "")))
                searchDocumentService.index(alpha.document("tx-$u", "Tx", project, identifiers = listOf(u)))
                search(u).items.map { it.key }
            }
        }
        assertEquals(listOf("tx-$u"), found)
    }

    @Test
    fun `A failed write of a search document does not fail the creation of the entity`() {
        val errors = { meterRegistry.find("ontrack_search_index_errors").tag("type", TestAlphaSearchDocumentIndexer.TYPE).counter()?.count() ?: 0.0 }
        val before = errors()
        val name = uid("P")
        asAdmin {
            val project = structureService.newProject(
                Project.of(NameDescription.nd(name, FailingSearchDocumentListener.DESCRIPTION))
            )
            assertNotNull(structureService.findProjectByID(project.id))
        }
        assertNotNull(asAdmin { structureService.findProjectByName(name).orElse(null) }, "Project created")
        assertEquals(before + 1, errors())
    }

    @Test
    fun `Documents are deleted with their project`() {
        val u = token()
        val project = project()
        index(
            alpha.document("a-$u", "A", project, identifiers = listOf(u)),
            beta.document("b-$u", "B", project, identifiers = listOf(u)),
        )
        assertEquals(2, asAdmin { search(u).total })
        asAdmin { structureService.deleteProject(project.id) }
        assertEquals(0, asAdmin { search(u).total })
    }

    @Test
    fun `Deleting a document by key`() {
        val u = token()
        val project = project()
        index(alpha.document("a-$u", "A", project, identifiers = listOf(u)))
        asAdmin { searchDocumentService.delete(TestAlphaSearchDocumentIndexer.TYPE, "a-$u") }
        assertEquals(0, asAdmin { search(u).total })
    }

    @Test
    fun `Indexing several documents at once creates or replaces them`() {
        val u = token()
        val project = project()
        index(alpha.document("a-$u", "Old", project, identifiers = listOf(u)))
        asAdmin {
            searchDocumentService.index(
                listOf(
                    alpha.document("a-$u", "New", project, identifiers = listOf(u)),
                    alpha.document("b-$u", "Other", project, identifiers = listOf(u)),
                )
            )
        }
        assertEquals(listOf("New", "Other"), asAdmin { search(u).items.map { it.title }.sorted() })
    }

    @Test
    fun `Inserting documents if absent keeps the existing ones as they are`() {
        val u = token()
        val project = project()
        index(alpha.document("existing-$u", "Existing", project, identifiers = listOf(u)))
        val inserted = asAdmin {
            searchDocumentService.insertIfAbsent(
                listOf(
                    alpha.document("existing-$u", "Replaced", project, identifiers = listOf(u)),
                    alpha.document("new-$u", "New", project, identifiers = listOf(u)),
                )
            )
        }
        assertEquals(1, inserted)
        assertEquals(listOf("Existing", "New"), asAdmin { search(u).items.map { it.title }.sorted() })
    }

    @Test
    fun `A failed insertion of documents does not fail the caller and is counted`() {
        val errors = { meterRegistry.find("ontrack_search_index_errors").tag("type", TestAlphaSearchDocumentIndexer.TYPE).counter()?.count() ?: 0.0 }
        val before = errors()
        val u = token()
        val project = project()
        val inserted = asAdmin {
            searchDocumentService.insertIfAbsent(
                listOf(alpha.document("failing-$u", "Not storable \u0000", project, identifiers = listOf(u)))
            )
        }
        assertEquals(0, inserted)
        assertEquals(before + 1, errors())
        // The transaction of the caller goes on
        index(alpha.document("ok-$u", "OK", project, identifiers = listOf(u)))
        assertEquals(listOf("ok-$u"), asAdmin { search(u).items.map { it.key } })
    }

    /**
     * The rebuild runs in its own transactions: the documents of the test must be committed.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `Rebuild writes the documents of the indexer and deletes the stale ones`() {
        val u = token()
        val project = project()
        index(alpha.document("stale-$u", "Stale", project, identifiers = listOf(u)))
        alpha.source += alpha.document("kept-$u", "Kept", project, identifiers = listOf(u))
        alpha.source += alpha.document("new-$u", "New", project, identifiers = listOf(u))
        searchService.reindex(TestAlphaSearchDocumentIndexer.TYPE)
        assertEquals(listOf("kept-$u", "new-$u"), asAdmin { search(u).items.map { it.key }.sorted() })
    }

    /**
     * The rebuild runs in its own transactions: the documents of the test must be committed.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `A message says the index is being built while a type is rebuilding`() {
        val u = token()
        val project = project()
        index(alpha.document("a-$u", "A", project, identifiers = listOf(u)))
        alpha.source += alpha.document("a-$u", "A", project, identifiers = listOf(u))
        val blocker = CountDownLatch(1)
        alpha.blocker = blocker
        val rebuild = thread { searchService.reindex(TestAlphaSearchDocumentIndexer.TYPE) }
        try {
            // Waiting for the rebuild to start
            var results: SearchResults
            val start = System.currentTimeMillis()
            do {
                results = asAdmin { search(u) }
            } while (results.message == null && System.currentTimeMillis() - start < 10_000)
            assertEquals("Search index is being built", results.message)
            assertEquals(listOf("a-$u"), results.items.map { it.key }, "What exists so far is still returned")
        } finally {
            blocker.countDown()
            rebuild.join()
        }
        assertNull(asAdmin { search(u) }.message)
    }

}
