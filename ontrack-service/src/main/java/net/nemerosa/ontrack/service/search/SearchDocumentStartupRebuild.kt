package net.nemerosa.ontrack.service.search

import net.nemerosa.ontrack.job.*
import net.nemerosa.ontrack.model.structure.SearchDocumentIndexer
import net.nemerosa.ontrack.model.structure.SearchIndexStartupReset
import net.nemerosa.ontrack.model.support.JobProvider
import net.nemerosa.ontrack.model.support.OntrackConfigProperties
import net.nemerosa.ontrack.model.support.StorageService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

/**
 * Rebuilds, at startup, the search documents of every indexer which has not been rebuilt yet at its
 * current [document version][SearchDocumentIndexer.documentVersion].
 *
 * The rebuild of each indexer is a job of its own, with no schedule, tracked by its own storage key,
 * so that a new indexer, or a new version of the documents of an indexer, triggers only its own
 * rebuild. The `ontrack.config.search.index.reset` setting forces the rebuild of all of them.
 *
 * Several replicas may start side by side, and during a rolling upgrade the previous version still
 * writes documents in their previous format. So, once a replica has started, the job of each type to
 * rebuild is fired once in the cluster (#2077):
 *
 * - after `ontrack.config.search.index.startup-delay`, past the overlap of the replicas;
 * - unless a delayed fire of the job is already pending, requested by another replica;
 * - its run is skipped when the documents have been rebuilt meanwhile, unless forced by `reset`.
 *
 * While a type is rebuilt, search answers with what exists so far, and the replica which rebuilds
 * it says that the search index is being built.
 */
@Component
class SearchDocumentStartupRebuild(
    private val searchDocumentIndexers: List<SearchDocumentIndexer>,
    private val searchDocumentService: SearchDocumentServiceImpl,
    private val storageService: StorageService,
    private val ontrackConfigProperties: OntrackConfigProperties,
    private val jobScheduler: JobScheduler,
) : JobProvider, SearchIndexStartupReset {

    private val logger: Logger = LoggerFactory.getLogger(SearchDocumentStartupRebuild::class.java)

    @Volatile
    private var rebuilds: List<CompletableFuture<*>> = emptyList()

    override fun getStartingJobs(): Collection<JobRegistration> =
        searchDocumentIndexers.map { indexer ->
            JobRegistration(StartupRebuildJob(indexer), Schedule.NONE)
        }

    @EventListener(ApplicationReadyEvent::class)
    fun start() {
        launch(ontrackConfigProperties.search.index.startupDelay)
    }

    /**
     * Fires the rebuild of every type out of date, after a delay.
     *
     * @return Number of rebuilds fired by this call
     */
    internal fun launch(startupDelay: Duration): Int {
        val reset = ontrackConfigProperties.search.index.reset
        val due = searchDocumentIndexers.filter { indexer -> reset || !isUpToDate(indexer) }
        if (due.isEmpty()) {
            logger.info("[search] All search documents are up to date")
            return 0
        }
        val fired = due.mapNotNull { indexer ->
            val type = indexer.searchResultType.id
            val fire = jobScheduler.fireLater(startupRebuildKey(indexer), startupDelay).orElse(null)
            if (fire != null) {
                logger.info("[search][$type] Launching the rebuild of the search documents in $startupDelay")
            } else {
                logger.info("[search][$type] The rebuild of the search documents is already launched")
            }
            fire
        }
        rebuilds = rebuilds + fired
        return fired.size
    }

    private fun isUpToDate(indexer: SearchDocumentIndexer): Boolean =
        storageService.find(
            STORE,
            indexer.searchResultType.id,
            SearchDocumentRebuildStatus::class
        )?.version == indexer.documentVersion

    private fun rebuild(indexer: SearchDocumentIndexer, listener: JobRunListener) {
        val type = indexer.searchResultType.id
        if (!ontrackConfigProperties.search.index.reset && isUpToDate(indexer)) {
            listener.message("[search][$type] Skipping the rebuild of the search documents: they have been rebuilt meanwhile")
        } else {
            listener.message("[search][$type] Rebuilding the search documents")
            searchDocumentService.rebuild(indexer)
            storageService.store(STORE, type, SearchDocumentRebuildStatus(indexer.documentVersion))
        }
    }

    /**
     * Waits for the rebuilds fired by this replica.
     */
    override fun awaitCompletion() {
        rebuilds.forEach { rebuild ->
            try {
                rebuild.get()
            } catch (any: ExecutionException) {
                logger.error("[search] Cannot rebuild the search documents", any.cause)
            }
        }
    }

    private inner class StartupRebuildJob(
        private val indexer: SearchDocumentIndexer,
    ) : Job {
        override fun getKey(): JobKey = startupRebuildKey(indexer)
        override fun getTask() = JobRun { listener -> rebuild(indexer, listener) }
        override fun getDescription(): String = "Rebuild of the search documents at startup: ${indexer.indexerName}"
        override fun isDisabled(): Boolean = false
    }

    companion object {
        /**
         * Storage of the version of the documents of each type, when last rebuilt
         */
        internal val STORE: String = SearchDocumentStartupRebuild::class.java.name

        /**
         * Type of the startup rebuild jobs, apart from the
         * [reconciliation jobs][SearchDocumentIndexationJobs.rebuildJobType]
         */
        val startupRebuildJobType: JobType = SearchDocumentIndexationJobs.jobCategory
            .getType("startup-rebuild")
            .withName("Search documents rebuild at startup")

        /**
         * Key of the startup rebuild job of an indexer, fixed by the ID of its type of search result
         */
        fun startupRebuildKey(indexer: SearchDocumentIndexer): JobKey =
            startupRebuildJobType.getKey(indexer.searchResultType.id)
    }

    data class SearchDocumentRebuildStatus(val version: Int)
}
