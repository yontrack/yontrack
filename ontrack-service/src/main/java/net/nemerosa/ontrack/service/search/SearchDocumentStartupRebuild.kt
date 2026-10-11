package net.nemerosa.ontrack.service.search

import jakarta.annotation.PreDestroy
import kotlinx.coroutines.*
import net.nemerosa.ontrack.job.JobKey
import net.nemerosa.ontrack.job.JobRunLock
import net.nemerosa.ontrack.job.JobType
import net.nemerosa.ontrack.model.structure.SearchDocumentIndexer
import net.nemerosa.ontrack.model.structure.SearchIndexStartupReset
import net.nemerosa.ontrack.model.support.OntrackConfigProperties
import net.nemerosa.ontrack.model.support.StartupService
import net.nemerosa.ontrack.model.support.StorageService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Rebuilds, in the background at startup, the search documents of every indexer which has not been
 * rebuilt yet at its current [document version][SearchDocumentIndexer.documentVersion].
 *
 * The rebuild of each indexer is tracked by its own storage key, so that a new indexer, or a new
 * version of the documents of an indexer, triggers only its own rebuild. The
 * `ontrack.config.search.index.reset` setting forces the rebuild of all of them.
 *
 * Several instances may start side by side, as during a rolling upgrade, while the previous one is
 * still writing documents in their previous format. So the rebuild:
 *
 * - waits for `ontrack.config.search.index.startup-delay`, past the overlap of the instances;
 * - runs under the [JobRunLock] of the [indexer][startupRebuildKey]: an instance which finds it held
 *   by another one skips the rebuild;
 * - is skipped when another instance has completed it meanwhile, unless forced by `reset`.
 *
 * While a type is rebuilt, search answers with what exists so far, and the instance which rebuilds
 * it says that the search index is being built.
 */
@Component
class SearchDocumentStartupRebuild(
    private val searchDocumentIndexers: List<SearchDocumentIndexer>,
    private val searchDocumentService: SearchDocumentServiceImpl,
    private val storageService: StorageService,
    private val ontrackConfigProperties: OntrackConfigProperties,
    private val jobRunLock: JobRunLock,
) : StartupService, SearchIndexStartupReset {

    private val logger: Logger = LoggerFactory.getLogger(SearchDocumentStartupRebuild::class.java)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var rebuilds: List<Job> = emptyList()

    override fun getName(): String = "Rebuild of the search documents"

    /**
     * After the registration of the jobs
     */
    override fun startupOrder(): Int = StartupService.JOB_REGISTRATION + 1

    override fun start() {
        val reset = ontrackConfigProperties.search.index.reset
        val due = searchDocumentIndexers.filter { indexer -> reset || !isUpToDate(indexer) }
        if (due.isEmpty()) {
            logger.info("[search] All search documents are up to date")
        } else {
            val startupDelay = ontrackConfigProperties.search.index.startupDelay
            rebuilds = due.map { indexer ->
                val type = indexer.searchResultType.id
                logger.info("[search][$type] Launching the rebuild of the search documents in $startupDelay")
                scope.launch {
                    delay(startupDelay.toMillis())
                    rebuild(indexer, reset)
                }
            }
        }
    }

    private fun isUpToDate(indexer: SearchDocumentIndexer): Boolean =
        storageService.find(
            STORE,
            indexer.searchResultType.id,
            SearchDocumentRebuildStatus::class
        )?.version == indexer.documentVersion

    private fun rebuild(indexer: SearchDocumentIndexer, reset: Boolean) {
        val type = indexer.searchResultType.id
        try {
            val lock = jobRunLock.tryLock(startupRebuildKey(indexer))
            if (lock == null) {
                logger.info("[search][$type] Skipping the rebuild of the search documents: another instance is rebuilding them")
                return
            }
            try {
                if (!reset && isUpToDate(indexer)) {
                    logger.info("[search][$type] Skipping the rebuild of the search documents: another instance has rebuilt them")
                } else {
                    searchDocumentService.rebuild(indexer)
                    storageService.store(STORE, type, SearchDocumentRebuildStatus(indexer.documentVersion))
                }
            } finally {
                lock.release()
            }
        } catch (any: Exception) {
            logger.error("[search][$type] Cannot rebuild the search documents", any)
        }
    }

    override fun awaitCompletion() {
        runBlocking { rebuilds.joinAll() }
    }

    /**
     * A rebuild still waiting for its delay is not run.
     */
    @PreDestroy
    fun cancel() {
        scope.cancel()
    }

    companion object {
        private val STORE: String = SearchDocumentStartupRebuild::class.java.name

        /**
         * Type of the keys of the [run locks][JobRunLock] of the startup rebuilds, apart from the
         * key of the [reconciliation job][SearchDocumentIndexationJobs.rebuildJobType]: a
         * reconciliation run by a previous version of Yontrack, still running side by side, must
         * not make this one skip the rebuild of the documents in their new format.
         */
        val startupRebuildJobType: JobType = SearchDocumentIndexationJobs.jobCategory
            .getType("startup-rebuild")
            .withName("Search documents rebuild at startup")

        /**
         * Key of the lock of the startup rebuild of an indexer, fixed by the ID of its type of
         * search result, so that it is the same across instances and versions.
         */
        fun startupRebuildKey(indexer: SearchDocumentIndexer): JobKey =
            startupRebuildJobType.getKey(indexer.searchResultType.id)
    }

    data class SearchDocumentRebuildStatus(val version: Int)
}
