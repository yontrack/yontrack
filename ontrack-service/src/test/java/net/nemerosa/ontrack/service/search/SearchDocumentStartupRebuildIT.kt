package net.nemerosa.ontrack.service.search

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.waitUntil
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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

/**
 * Rebuild of the search documents at startup, a job per type, fired once in the cluster by the
 * replicas which start (#2077).
 *
 * Not transactional: the rebuilds run as jobs.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@OptIn(ExperimentalTime::class)
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

    private fun rebuilds() = testAlphaSearchDocumentIndexer.indexAllCalls.get()

    private val key get() = SearchDocumentStartupRebuild.startupRebuildKey(indexer)

    private fun history() =
        jobHistoryService.getHistory(key, Time.now.minusHours(1), Time.now.plusHours(1), skipErrors = false)
            .sortedBy { it.startedAt }

    private fun runs() = history().size

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

    /**
     * Fires the rebuild of the test type as a replica which read that it is out of date, once the
     * trigger of any previous fire is gone - Quartz deletes it just after its run - and waits for
     * its run.
     */
    private fun fireAs(replica: String) {
        waitUntil("$replica fires the rebuild", interval = 100.milliseconds, timeout = 10.seconds) {
            searchDocumentStartupRebuild.fire(listOf(indexer), Duration.ZERO) == 1
        }
        searchDocumentStartupRebuild.awaitCompletion()
    }

    /**
     * A second replica reads that the type is out of date before the first one stores its rebuild,
     * and fires its rebuild once the trigger of the first one is gone: its run skips (#2094).
     */
    @Test
    fun `A second fire after the rebuild of a type runs the job again, without rebuilding`() {
        outOfDate()
        val runsBefore = runs()
        val rebuildsBefore = rebuilds()
        assertEquals(1, searchDocumentStartupRebuild.launch(Duration.ZERO), "First replica")
        searchDocumentStartupRebuild.awaitCompletion()
        fireAs("Second replica")
        assertEquals(runsBefore + 2, runs(), "Two runs")
        assertEquals(rebuildsBefore + 1, rebuilds(), "One rebuild")
        val messages = history().takeLast(2).map { it.message }
        assertEquals(
            listOf(
                "[search][${indexer.searchResultType.id}] Rebuilding the search documents",
                "[search][${indexer.searchResultType.id}] Skipping the rebuild of the search documents: they have been rebuilt meanwhile",
            ),
            messages,
            "The skipped run says why it did nothing"
        )
    }

    /**
     * With `reset`, a run never skips: the documented caveat of `reset` (#2094).
     */
    @Test
    fun `With reset, a second fire after the rebuild of a type rebuilds it again`() {
        val reset = ontrackConfigProperties.search.index.reset
        ontrackConfigProperties.search.index.reset = true
        try {
            val runsBefore = runs()
            val rebuildsBefore = rebuilds()
            fireAs("First replica")
            fireAs("Second replica")
            assertEquals(runsBefore + 2, runs(), "Two runs")
            assertEquals(rebuildsBefore + 2, rebuilds(), "Two rebuilds")
        } finally {
            ontrackConfigProperties.search.index.reset = reset
        }
    }
}
