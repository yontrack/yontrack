package net.nemerosa.ontrack.service.search

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.job.JobRunLock
import net.nemerosa.ontrack.model.structure.SearchDocument
import net.nemerosa.ontrack.model.support.OntrackConfigProperties
import net.nemerosa.ontrack.model.support.StorageService
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Rebuild of the search documents at startup, by instances of Yontrack started side by side
 * against the same database, as during a rolling upgrade.
 *
 * Not transactional: the rebuilds run in the background, and each instance takes its lock on a
 * connection of its own.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SearchDocumentStartupRebuildIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var searchDocumentService: SearchDocumentServiceImpl

    @Autowired
    private lateinit var storageService: StorageService

    @Autowired
    private lateinit var jobRunLock: JobRunLock

    private val appender = ListAppender<ILoggingEvent>()

    private val logger = LoggerFactory.getLogger(SearchDocumentStartupRebuild::class.java) as Logger

    @BeforeEach
    fun captureLogs() {
        appender.start()
        logger.addAppender(appender)
    }

    @AfterEach
    fun releaseLogs() {
        logger.detachAppender(appender)
        appender.stop()
    }

    private fun logs(): List<String> = appender.list.map { it.formattedMessage }

    /**
     * Indexer of a new type, which records when its documents are rebuilt.
     */
    private class RecordingIndexer : AbstractTestSearchDocumentIndexer(uid("test-startup-"), 2000) {

        val rebuilds = CopyOnWriteArrayList<Long>()

        /**
         * Counted down when a rebuild starts
         */
        val started = CountDownLatch(1)

        override fun indexAll(processor: (SearchDocument) -> Unit) {
            rebuilds += System.nanoTime()
            started.countDown()
            super.indexAll(processor)
        }
    }

    /**
     * An instance of Yontrack, as far as the startup rebuild of the search documents goes.
     */
    private fun instance(indexer: RecordingIndexer, startupDelay: Duration = Duration.ZERO) =
        SearchDocumentStartupRebuild(
            searchDocumentIndexers = listOf(indexer),
            searchDocumentService = searchDocumentService,
            storageService = storageService,
            ontrackConfigProperties = OntrackConfigProperties().apply {
                search.index.startupDelay = startupDelay
            },
            jobRunLock = jobRunLock,
        )

    @Test
    fun `Of two instances rebuilding the documents of a type together, one rebuilds them and the other one skips`() {
        val indexer = RecordingIndexer()
        // The first rebuild is held until the other instance has tried its own
        indexer.blocker = CountDownLatch(1)
        val first = instance(indexer)
        val second = instance(indexer)
        try {
            first.start()
            assertTrue(indexer.started.await(30, TimeUnit.SECONDS), "First rebuild started")
            second.start()
            second.awaitCompletion()
        } finally {
            indexer.blocker?.countDown()
        }
        first.awaitCompletion()

        assertEquals(1, indexer.rebuilds.size, "One rebuild")
        assertTrue(
            logs().any {
                it == "[search][${indexer.searchResultType.id}] Skipping the rebuild of the search documents: another instance is rebuilding them"
            },
            "The skipped rebuild is logged"
        )
    }

    @Test
    fun `An instance does not rebuild the documents of a type which another instance has rebuilt meanwhile`() {
        val indexer = RecordingIndexer()
        val first = instance(indexer)
        // Both instances start, and find the type to be rebuilt
        val second = instance(indexer, startupDelay = Duration.ofSeconds(3))
        first.start()
        second.start()
        first.awaitCompletion()
        assertEquals(1, indexer.rebuilds.size, "Rebuilt by the first instance")
        second.awaitCompletion()

        assertEquals(1, indexer.rebuilds.size, "One rebuild")
        assertTrue(
            logs().any {
                it == "[search][${indexer.searchResultType.id}] Skipping the rebuild of the search documents: another instance has rebuilt them"
            },
            "The skipped rebuild is logged"
        )
    }

    @Test
    fun `The documents are not rebuilt before the startup delay`() {
        val indexer = RecordingIndexer()
        val delay = Duration.ofSeconds(2)
        val instance = instance(indexer, startupDelay = delay)
        val start = System.nanoTime()
        instance.start()
        assertTrue(indexer.rebuilds.isEmpty(), "Not rebuilt at startup")
        instance.awaitCompletion()

        assertEquals(1, indexer.rebuilds.size, "Rebuilt once the delay has passed")
        val waited = Duration.ofNanos(indexer.rebuilds.first() - start)
        assertTrue(waited >= delay, "Rebuilt after $waited, not before the delay of $delay")
    }

}
