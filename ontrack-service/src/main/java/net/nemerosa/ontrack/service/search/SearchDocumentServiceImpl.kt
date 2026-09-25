package net.nemerosa.ontrack.service.search

import io.micrometer.core.instrument.MeterRegistry
import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.model.metrics.increment
import net.nemerosa.ontrack.model.metrics.time
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.ProjectEntityID
import net.nemerosa.ontrack.model.structure.SearchDocument
import net.nemerosa.ontrack.model.structure.SearchDocumentIndexer
import net.nemerosa.ontrack.model.structure.SearchDocumentService
import net.nemerosa.ontrack.model.support.OntrackConfigProperties
import net.nemerosa.ontrack.repository.search.SearchDocumentPage
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
            indexer.indexAll { document ->
                buffer += document
                if (buffer.size >= batchSize) {
                    flush()
                }
            }
        }
        flush()
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
     * Deletes all the documents of a type
     */
    fun clear(type: String) {
        searchDocumentRepository.deleteAll(type)
    }

    /**
     * Searches for documents.
     */
    fun search(
        query: String,
        scope: SearchDocumentScope,
        offset: Int,
        size: Int,
        perType: Int?,
        highlight: Boolean = false,
    ): SearchDocumentPage? = searchDocumentRepository.search(query, scope, offset, size, perType, highlight)

}
