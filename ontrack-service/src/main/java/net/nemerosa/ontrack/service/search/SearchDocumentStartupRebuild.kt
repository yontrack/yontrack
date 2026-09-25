package net.nemerosa.ontrack.service.search

import kotlinx.coroutines.*
import net.nemerosa.ontrack.model.structure.SearchDocumentIndexer
import net.nemerosa.ontrack.model.structure.SearchIndexStartupReset
import net.nemerosa.ontrack.model.support.OntrackConfigProperties
import net.nemerosa.ontrack.model.support.StartupService
import net.nemerosa.ontrack.model.support.StorageService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Rebuilds, asynchronously at startup, the search documents of every indexer which has not been
 * rebuilt yet at its current [document version][SearchDocumentIndexer.documentVersion].
 *
 * The rebuild of each indexer is tracked by its own storage key, so that a new indexer, or a new
 * version of the documents of an indexer, triggers only its own rebuild. The
 * `ontrack.config.search.index.reset` setting forces the rebuild of all of them.
 *
 * While a type is rebuilt, search answers with what exists so far, and says that the search index
 * is being built.
 */
@Component
class SearchDocumentStartupRebuild(
    private val searchDocumentIndexers: List<SearchDocumentIndexer>,
    private val searchDocumentService: SearchDocumentServiceImpl,
    private val storageService: StorageService,
    private val ontrackConfigProperties: OntrackConfigProperties,
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
        val due = searchDocumentIndexers.filter { indexer ->
            reset || storageService.find(
                STORE,
                indexer.searchResultType.id,
                SearchDocumentRebuildStatus::class
            )?.version != indexer.documentVersion
        }
        if (due.isEmpty()) {
            logger.info("[search] All search documents are up to date")
        } else {
            rebuilds = due.map { indexer ->
                val type = indexer.searchResultType.id
                logger.info("[search][$type] Launching the rebuild of the search documents")
                scope.launch {
                    try {
                        searchDocumentService.rebuild(indexer)
                        storageService.store(STORE, type, SearchDocumentRebuildStatus(indexer.documentVersion))
                    } catch (any: Exception) {
                        logger.error("[search][$type] Cannot rebuild the search documents", any)
                    }
                }
            }
        }
    }

    override fun awaitCompletion() {
        runBlocking { rebuilds.joinAll() }
    }

    companion object {
        private val STORE: String = SearchDocumentStartupRebuild::class.java.name
    }

    data class SearchDocumentRebuildStatus(val version: Int)
}
