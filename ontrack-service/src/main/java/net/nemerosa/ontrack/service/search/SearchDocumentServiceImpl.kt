package net.nemerosa.ontrack.service.search

import io.micrometer.core.instrument.MeterRegistry
import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.model.metrics.increment
import net.nemerosa.ontrack.model.metrics.time
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityID
import net.nemerosa.ontrack.model.structure.SearchDocument
import net.nemerosa.ontrack.model.structure.SearchDocumentIndexer
import net.nemerosa.ontrack.model.structure.SearchDocumentReference
import net.nemerosa.ontrack.model.structure.SearchDocumentService
import net.nemerosa.ontrack.model.support.OntrackConfigProperties
import net.nemerosa.ontrack.repository.search.SearchDocumentPage
import net.nemerosa.ontrack.repository.search.SearchDocumentReferenceName
import net.nemerosa.ontrack.repository.search.SearchDocumentRepository
import net.nemerosa.ontrack.repository.search.SearchDocumentScope
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.ConcurrentHashMap

/**
 * Postgres storage of the search documents.
 */
@Service
class SearchDocumentServiceImpl(
    private val searchDocumentRepository: SearchDocumentRepository,
    private val securityService: SecurityService,
    private val meterRegistry: MeterRegistry,
    private val ontrackConfigProperties: OntrackConfigProperties,
    platformTransactionManager: PlatformTransactionManager,
) : SearchDocumentService {

    private val logger: Logger = LoggerFactory.getLogger(SearchDocumentServiceImpl::class.java)

    /**
     * Runs a write in a savepoint of the current transaction, or in a new transaction when there is
     * none. In Postgres a failed statement aborts the whole transaction: the savepoint is what
     * keeps a failed write from failing the change it belongs to.
     */
    private val savepoint = TransactionTemplate(platformTransactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_NESTED
    }

    /**
     * Types being rebuilt
     */
    private val rebuilding: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /**
     * Types whose documents are being rebuilt.
     */
    val rebuildingTypes: Set<String> get() = rebuilding.toSet()

    override fun index(document: SearchDocument) {
        write(document.type, "index ${document.key}") {
            searchDocumentRepository.save(listOf(document), Time.now())
        }
    }

    override fun index(documents: List<SearchDocument>) {
        documents.groupBy { it.type }.forEach { (type, typeDocuments) ->
            write(type, "index ${typeDocuments.size} documents") {
                searchDocumentRepository.save(typeDocuments, Time.now())
            }
        }
    }

    override fun insertIfAbsent(documents: List<SearchDocument>): Int =
        documents.groupBy { it.type }.entries.sumOf { (type, typeDocuments) ->
            var inserted = 0
            write(type, "insert ${typeDocuments.size} documents") {
                inserted = searchDocumentRepository.insertIfAbsent(typeDocuments, Time.now())
            }
            inserted
        }

    override fun delete(type: String, key: String) {
        write(type, "delete $key") {
            searchDocumentRepository.delete(type, key)
        }
    }

    override fun deleteForProjectExcept(type: String, projectId: Int, keys: Collection<String>): Int {
        var deleted = 0
        write(type, "delete the documents of project $projectId") {
            deleted = searchDocumentRepository.deleteForProjectExcept(type, projectId, keys)
        }
        return deleted
    }

    /**
     * Deletes the documents of every type describing an entity which is being deleted: the
     * documents of this entity and, for a branch, those of its builds. Only the deletion of a
     * project cascades to its documents in the database.
     *
     * Must be called before the entity is deleted, in the transaction of its deletion.
     */
    fun deleteForEntity(entity: ProjectEntityID) {
        write(entity.type.name.lowercase(), "delete the documents of ${entity.type.name} ${entity.id}") {
            searchDocumentRepository.deleteForEntity(entity)
        }
    }

    /**
     * Runs a write, never failing: a failure is rolled back to the savepoint, logged and counted.
     *
     * @return `true` if the write succeeded
     */
    private fun write(type: String, description: String, code: () -> Unit): Boolean =
        try {
            savepoint.executeWithoutResult { code() }
            true
        } catch (any: Exception) {
            logger.error("[search][$type] Cannot $description", any)
            meterRegistry.increment(SearchIndexMetrics.indexErrors, SearchIndexMetrics.METRIC_TYPE to type)
            false
        }

    override fun rebuild(indexer: SearchDocumentIndexer) {
        val type = indexer.searchResultType.id
        rebuilding += type
        try {
            meterRegistry.time(SearchIndexMetrics.rebuild, SearchIndexMetrics.METRIC_TYPE to type) {
                doRebuild(indexer, type)
            }
        } finally {
            rebuilding -= type
        }
    }

    private fun doRebuild(indexer: SearchDocumentIndexer, type: String) {
        logger.info("[search][$type] Rebuilding the search documents")
        val start = Time.now()
        val (count, ok) = writeAll(type) { processor -> indexer.indexAll(processor) }
        // Deleting the documents not provided by this rebuild, only if all of them could be written
        if (ok) {
            write(type, "delete stale documents") {
                val deleted = searchDocumentRepository.deleteIndexedBefore(type, start)
                logger.info("[search][$type] Rebuilt $count documents, deleted $deleted stale ones")
            }
        } else {
            logger.warn("[search][$type] Rebuilt $count documents with errors, stale documents are kept")
        }
    }

    /**
     * Re-indexes the documents of an indexer referring to a renamed project or branch through one of
     * its [rename scopes][SearchDocumentIndexer.renameScopes]: those it provides are written, and
     * those of its type referring to the entity which it did not provide are deleted.
     *
     * Never fails: a failure is logged and counted, like a failed write.
     *
     * @param indexer Indexer whose documents are re-indexed
     * @param scope One of its rename scopes
     * @param entity The renamed project or branch
     */
    fun reindexRenamed(indexer: SearchDocumentIndexer, scope: SearchDocumentReference, entity: ProjectEntity) {
        val type = indexer.searchResultType.id
        val description = "${entity.projectEntityType.name} ${entity.id()} at ${scope.path.joinToString(".")}"
        try {
            val start = Time.now()
            val (count, ok) = writeAll(type) { processor -> indexer.indexRenamed(scope, entity, processor) }
            if (ok) {
                write(type, "delete the stale documents of the renamed $description") {
                    val deleted = searchDocumentRepository.deleteReferringIndexedBefore(type, scope.path, entity.id(), start)
                    logger.info("[search][$type] Re-indexed $count documents of the renamed $description, deleted $deleted stale ones")
                }
            } else {
                logger.warn("[search][$type] Re-indexed $count documents of the renamed $description with errors, stale documents are kept")
            }
        } catch (any: Exception) {
            logger.error("[search][$type] Cannot re-index the documents of the renamed $description", any)
            meterRegistry.increment(SearchIndexMetrics.indexErrors, SearchIndexMetrics.METRIC_TYPE to type)
        }
    }

    /**
     * Writes all the documents provided as administrator, in batches.
     *
     * @return Number of documents written, and whether all of them could be
     */
    private fun writeAll(type: String, provider: (processor: (SearchDocument) -> Unit) -> Unit): Pair<Int, Boolean> {
        val batchSize = ontrackConfigProperties.search.index.batch
        val buffer = mutableListOf<SearchDocument>()
        var count = 0
        var ok = true
        val flush = {
            if (buffer.isNotEmpty()) {
                val documents = buffer.toList()
                buffer.clear()
                count += documents.size
                ok = write(type, "index ${documents.size} documents") {
                    searchDocumentRepository.save(documents, Time.now())
                } && ok
                if (ontrackConfigProperties.search.index.logging) {
                    logger.info("[search][$type] Rebuilt $count documents so far")
                }
            }
        }
        securityService.asAdmin {
            provider { document ->
                buffer += document
                if (buffer.size >= batchSize) {
                    flush()
                }
            }
        }
        flush()
        return count to ok
    }

    /**
     * Current names of the projects and branches the documents found refer to.
     */
    fun findReferenceNames(projectIds: Collection<Int>, branchIds: Collection<Int>): List<SearchDocumentReferenceName> =
        searchDocumentRepository.findReferenceNames(projectIds, branchIds)

    /**
     * Deletes all the documents of a type
     */
    fun clear(type: String) {
        searchDocumentRepository.deleteAll(type)
    }

    /**
     * Runs a search in a read-only transaction - the one of the caller when there is one - so that
     * the settings of the search (`work_mem`, see [SearchDocumentRepository.prepareSearchTransaction])
     * end with it.
     */
    private val readOnly = TransactionTemplate(platformTransactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRED
        isReadOnly = true
    }

    /**
     * Searches for documents, with the [cap of the counts][net.nemerosa.ontrack.model.support.SearchConfigProperties.countCap]
     * and the [work_mem][net.nemerosa.ontrack.model.support.SearchConfigProperties.workMem] of the
     * configuration.
     */
    fun search(
        query: String,
        scope: SearchDocumentScope,
        offset: Int,
        size: Int,
        perType: Int?,
        highlight: Boolean = false,
    ): SearchDocumentPage? = readOnly.execute {
        val config = ontrackConfigProperties.search
        searchDocumentRepository.prepareSearchTransaction(config.workMem)
        searchDocumentRepository.search(query, scope, offset, size, perType, highlight, config.countCap)
    }

}
