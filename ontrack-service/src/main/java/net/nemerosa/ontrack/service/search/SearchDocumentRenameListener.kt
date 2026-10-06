package net.nemerosa.ontrack.service.search

import kotlinx.coroutines.*
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.events.EventListener
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.structure.SearchDocumentIndexer
import net.nemerosa.ontrack.model.structure.SearchDocumentRenameReindexation
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.util.concurrent.ConcurrentHashMap

/**
 * Re-indexes, when a project or a branch is renamed, the search documents carrying its name in
 * what they are matched on — their title or identifiers — as declared by the
 * [rename scopes][SearchDocumentIndexer.renameScopes] of the indexers.
 *
 * Only a rename triggers it, not any update: the update events carry the
 * [previous name][EventFactory.PREVIOUS_NAME] of the project or branch only when they rename it.
 *
 * The re-indexation runs asynchronously, after the transaction of the rename is committed, so that
 * a rename never carries a bulk rewrite, and never fails because of the search: a failure is logged
 * and counted in `ontrack_search_index_errors`, and the reconciliation of the type repairs it. The
 * names shown by the results do not wait for it, since they are resolved when searching.
 *
 * The re-indexations run one at a time, in the order of the commits of the renames: two quick renames
 * of a project end with the documents of the last one.
 */
@Component
class SearchDocumentRenameListener(
    private val searchDocumentIndexers: List<SearchDocumentIndexer>,
    private val searchDocumentService: SearchDocumentServiceImpl,
) : EventListener, SearchDocumentRenameReindexation {

    private val logger: Logger = LoggerFactory.getLogger(SearchDocumentRenameListener::class.java)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    /**
     * Re-indexations in progress
     */
    private val running: MutableSet<Job> = ConcurrentHashMap.newKeySet()

    override fun onEvent(event: Event) {
        if (event.values[EventFactory.PREVIOUS_NAME] == null) {
            return
        }
        when (event.eventType) {
            EventFactory.UPDATE_PROJECT ->
                onRenamed(event.getEntity<Project>(ProjectEntityType.PROJECT))

            EventFactory.UPDATE_BRANCH ->
                onRenamed(event.getEntity<Branch>(ProjectEntityType.BRANCH))
        }
    }

    private fun onRenamed(entity: ProjectEntity) {
        val scopes = searchDocumentIndexers.flatMap { indexer ->
            indexer.renameScopes
                .filter { it.type == entity.projectEntityType }
                .map { indexer to it }
        }
        if (scopes.isEmpty()) {
            return
        }
        val reindex = {
            launch {
                scopes.forEach { (indexer, scope) ->
                    searchDocumentService.reindexRenamed(indexer, scope, entity)
                }
            }
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() {
                    reindex()
                }
            })
        } else {
            reindex()
        }
    }

    private fun launch(code: () -> Unit) {
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                code()
            } catch (any: Exception) {
                logger.error("[search] Cannot re-index the search documents after a rename", any)
            }
        }
        running += job
        job.invokeOnCompletion { running -= job }
        job.start()
    }

    override fun awaitCompletion() {
        runBlocking { running.toList().joinAll() }
    }
}
