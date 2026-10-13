package net.nemerosa.ontrack.service.search

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.job.JobScheduler
import net.nemerosa.ontrack.model.job.JobHistoryService
import net.nemerosa.ontrack.model.structure.SearchDocumentIndexer
import net.nemerosa.ontrack.model.support.StorageService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Rebuild of the search documents at startup, a job per type, fired once in the cluster by the
 * replicas which start (#2077).
 *
 * Not transactional: the rebuilds run as jobs.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SearchDocumentStartupRebuildIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var searchDocumentStartupRebuild: SearchDocumentStartupRebuild

    @Autowired
    private lateinit var searchDocumentIndexers: List<SearchDocumentIndexer>

    @Autowired
    private lateinit var testAlphaSearchDocumentIndexer: TestAlphaSearchDocumentIndexer

    @Autowired
    private lateinit var storageService: StorageService

    @Autowired
    private lateinit var jobScheduler: JobScheduler

    @Autowired
    private lateinit var jobHistoryService: JobHistoryService

    private val indexer: SearchDocumentIndexer get() = testAlphaSearchDocumentIndexer

    private val key get() = SearchDocumentStartupRebuild.startupRebuildKey(indexer)

    private fun runs() =
        jobHistoryService.getHistory(key, Time.now.minusHours(1), Time.now.plusHours(1), skipErrors = false).size

    /**
     * The documents of the test type are out of date
     */
    private fun outOfDate() {
        storageService.delete(SearchDocumentStartupRebuild.STORE, indexer.searchResultType.id)
    }

    @Test
    fun `The rebuild of every type is a job`() {
        val keys = jobScheduler.getJobKeysOfType(SearchDocumentStartupRebuild.startupRebuildJobType)
        assertEquals(
            searchDocumentIndexers.map { SearchDocumentStartupRebuild.startupRebuildKey(it) }.toSet(),
            keys.toSet(),
        )
    }

    @Test
    fun `The documents of a type out of date are rebuilt once, after the startup delay`() {
        outOfDate()
        val before = runs()
        assertEquals(1, searchDocumentStartupRebuild.launch(Duration.ofSeconds(2)), "Rebuild fired")
        assertEquals(0, searchDocumentStartupRebuild.launch(Duration.ofSeconds(2)), "No second rebuild while the first one is pending")
        assertEquals(before, runs(), "Not rebuilt before the delay")
        searchDocumentStartupRebuild.awaitCompletion()
        assertEquals(before + 1, runs(), "Rebuilt once")
        assertNotNull(
            storageService.find(
                SearchDocumentStartupRebuild.STORE,
                indexer.searchResultType.id,
                SearchDocumentStartupRebuild.SearchDocumentRebuildStatus::class
            ),
            "The version of the documents is stored"
        ) {
            assertEquals(indexer.documentVersion, it.version)
        }
    }

    @Test
    fun `The documents of a type up to date are not rebuilt`() {
        outOfDate()
        searchDocumentStartupRebuild.launch(Duration.ZERO)
        searchDocumentStartupRebuild.awaitCompletion()
        val before = runs()
        assertEquals(0, searchDocumentStartupRebuild.launch(Duration.ZERO), "No rebuild fired")
        assertEquals(before, runs(), "Not rebuilt again")
    }
}
