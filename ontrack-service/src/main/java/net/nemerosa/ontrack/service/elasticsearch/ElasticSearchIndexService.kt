package net.nemerosa.ontrack.service.elasticsearch

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch._types.ElasticsearchException
import co.elastic.clients.elasticsearch.core.BulkRequest
import co.elastic.clients.elasticsearch.core.DeleteRequest
import co.elastic.clients.elasticsearch.core.GetRequest
import co.elastic.clients.elasticsearch.core.IndexRequest
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest
import co.elastic.clients.elasticsearch.indices.ExistsRequest
import co.elastic.clients.elasticsearch.indices.RefreshRequest
import io.micrometer.core.instrument.MeterRegistry
import net.nemerosa.ontrack.model.structure.*
import net.nemerosa.ontrack.model.support.OntrackConfigProperties
import net.nemerosa.ontrack.model.support.time
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional


@Service
@Transactional
class ElasticSearchIndexService(
    private val client: ElasticsearchClient,
    private val ontrackConfigProperties: OntrackConfigProperties,
    private val meterRegistry: MeterRegistry,
) : SearchIndexService {

    private val logger: Logger = LoggerFactory.getLogger(ElasticSearchIndexService::class.java)

    private fun <T : SearchItem> refreshIndex(indexer: SearchIndexer<T>) {
        logger.debug("Refreshing index ${indexer.indexName}")
        val refreshRequest = RefreshRequest.Builder().index(indexer.indexName).build()
        client.indices().refresh(refreshRequest)
    }

    override fun <T : SearchItem> index(indexer: SearchIndexer<T>) {
        val batchSize = indexer.indexBatch ?: ontrackConfigProperties.search.index.batch
        logger.debug("Full indexation for ${indexer.indexName} with batch size = $batchSize")
        val buffer = mutableListOf<T>()
        meterRegistry.time(
            name = ElasticSearchIndexMetrics.esIndexAll,
            ElasticSearchIndexMetrics.METRIC_INDEX to indexer.indexName,
        ) {
            indexer.indexAll { item ->
                buffer.add(item)
                if (buffer.size == batchSize) {
                    index(indexer, buffer)
                    buffer.clear()
                }
            }
            // Remaining items
            index(indexer, buffer)
            // Refreshes the index
            immediateRefreshIfRequested(indexer)
        }
    }

    override fun <T : SearchItem> initIndex(indexer: SearchIndexer<T>) {
        logger.info("[elasticsearch][index][${indexer.indexName}] Init")
        val indexExists = client.indices().exists(ExistsRequest.Builder().index(indexer.indexName).build()).value()
        if (!indexExists) {
            logger.info("[elasticsearch][index][${indexer.indexName}] Creating index")
            val request = CreateIndexRequest.Builder()
                .index(indexer.indexName)
                .let { builder: CreateIndexRequest.Builder ->
                    indexer.initIndex(builder)
                }.build()
            try {
                client.indices().create(request)
            } catch (ex: ElasticsearchException) {
                if (ontrackConfigProperties.search.index.ignoreExisting) {
                    logger.info("[elasticsearch][index][${indexer.indexName}] Cannot create index because already existing. Ignoring issue (likely happens during test).")
                } else {
                    throw ex
                }
            }
        }
    }

    override fun deleteIndex(indexName: String) {
        val indexExists = client.indices().exists(ExistsRequest.Builder().index(indexName).build()).value()
        if (indexExists) {
            client.indices().delete(DeleteIndexRequest.Builder().index(indexName).build())
        }
    }

    override fun <T : SearchItem> resetIndex(indexer: SearchIndexer<T>, reindex: Boolean, logErrors: Boolean): Boolean {
        // Deletes the index
        client.indices().delete(DeleteIndexRequest.Builder().index(indexer.indexName).build())
        // Re-creates the index
        initIndex(indexer)
        // Re-index if requested
        if (reindex) {
            try {
                index(indexer)
            } catch (any: Exception) {
                if (logErrors) {
                    logger.error("[elasticsearch][reset][${indexer.indexName}] Cannot re-index data", any)
                    return false
                } else {
                    throw any
                }
            }
        }
        // Refreshes the index always
        refreshIndex(indexer)
        // OK
        return true
    }

    private fun <T : SearchItem> index(indexer: SearchIndexer<T>, items: List<T>) {
        val bulkRequestBuilder = BulkRequest.Builder().index(indexer.indexName)
        var operationsCount = 0
        items.forEach { item ->
            val operation = BulkOperation.Builder()
                .index<Any?> { op ->
                    op
                        .id(item.id)
                        .index(indexer.indexName)
                        .document(item.fields)
                }.build()
            bulkRequestBuilder.operations(operation)
            operationsCount++
        }
        if (operationsCount > 0) {
            val bulkRequest = bulkRequestBuilder.build()
            // Launching the indexation of this batch
            client.bulk(bulkRequest)
            // Refreshes the index
            immediateRefreshIfRequested(indexer)
        }
    }

    private fun <T : SearchItem> immediateRefreshIfRequested(indexer: SearchIndexer<T>) {
        if (ontrackConfigProperties.search.index.immediate) {
            refreshIndex(indexer)
        }
    }

    override val searchIndexesAvailable: Boolean = true

    override fun <T : SearchItem> createSearchIndex(indexer: SearchIndexer<T>, item: T) {
        logger.debug("Create index ${indexer.indexName}")
        val indexRequest = IndexRequest.Builder<Any>()
            .index(indexer.indexName)
            .id(item.id)
            .document(item.fields)
            .build()
        client.index(indexRequest)
        // Refreshes the index
        immediateRefreshIfRequested(indexer)
    }

    override fun <T : SearchItem> updateSearchIndex(indexer: SearchIndexer<T>, item: T) {
        logger.debug("Update index ${indexer.indexName}")
        val indexRequest = IndexRequest.Builder<Any>()
            .index(indexer.indexName)
            .id(item.id)
            .document(item.fields)
            .build()
        client.index(indexRequest)
        // Refreshes the index
        immediateRefreshIfRequested(indexer)
    }

    override fun <T : SearchItem> deleteSearchIndex(indexer: SearchIndexer<T>, id: String) {
        logger.debug("Delete index ${indexer.indexName}")
        val deleteRequest = DeleteRequest.Builder().index(indexer.indexName).id(id).build()
        client.delete(deleteRequest)
        // Refreshes the index
        immediateRefreshIfRequested(indexer)
    }

    override fun <T : SearchItem> batchSearchIndex(
        indexer: SearchIndexer<T>,
        items: Collection<T>,
        mode: BatchIndexMode
    ): BatchIndexResults {
        logger.debug("[search][batch-index] index={},items={},mode={}", indexer.indexName, items.size, mode)
        // Building the list of actions to take
        val bulk = items.fold(BatchSearchIndexBulk(indexer.indexName)) { acc, item ->
            val action = batchSearchIndexAction(indexer, item, mode)
            if (ontrackConfigProperties.search.index.logging && logger.isDebugEnabled) {
                if (action.action != null) {
                    logger.debug("[search][batch-index] index=${indexer.indexName},item=${item.id},mode=$mode,action=${action.action::class.java.simpleName}")
                } else if (ontrackConfigProperties.search.index.tracing) {
                    logger.debug("[search][batch-index] index=${indexer.indexName},item=${item.id},mode=$mode,action=none")
                }
            }
            acc + action
        }
        // Launching the indexation of this batch
        if (bulk.hasOperations) {
            val bulkRequest = bulk.bulk.build()
            logger.info("[search][batch-index] index=${indexer.indexName},items=${items.size},mode=$mode,actions=${bulkRequest.operations().size}")
            client.bulk(bulkRequest)
            // Refreshes the index
            immediateRefreshIfRequested(indexer)
        }
        // OK
        return bulk.results
    }

    private fun <T : SearchItem> batchSearchIndexAction(
        indexer: SearchIndexer<T>,
        item: T,
        mode: BatchIndexMode
    ): BatchSearchIndexAction {
        // Gets the existing item using its ID
        val getRequest = GetRequest.Builder()
            .index(indexer.indexName)
            .id(item.id)
            .build()
        val response = client.get(getRequest, Any::class.java)
        // If item exists
        return if (response.found()) {
            when (mode) {
                BatchIndexMode.KEEP -> BatchSearchIndexAction(null, BatchIndexResults.KEEP)
                BatchIndexMode.UPDATE -> BatchSearchIndexAction(
                    BulkOperation.Builder().index<Any> { indexBuilder ->
                        indexBuilder.index(indexer.indexName)
                        indexBuilder.id(item.id)
                        indexBuilder.document(item.fields)
                    }.build(),
                    BatchIndexResults.UPDATE
                )
            }
        }
        // If not existing
        else {
            BatchSearchIndexAction(
                BulkOperation.Builder().index<Any> { indexBuilder ->
                    indexBuilder.index(indexer.indexName)
                    indexBuilder.id(item.id)
                    indexBuilder.document(item.fields)
                }.build(),
                BatchIndexResults.ADD
            )
        }
    }

    private class BatchSearchIndexBulk private constructor(
        val bulk: BulkRequest.Builder,
        val results: BatchIndexResults,
        val hasOperations: Boolean
    ) {
        constructor(indexName: String) : this(
            BulkRequest.Builder().index(indexName),
            BatchIndexResults.NONE,
            false
        )

        operator fun plus(action: BatchSearchIndexAction) =
            BatchSearchIndexBulk(
                bulk = action.action?.let {
                    bulk.operations(it)
                } ?: bulk,
                results = results + action.results,
                hasOperations = hasOperations || (action.action != null)
            )
    }

    private class BatchSearchIndexAction(
        val action: BulkOperation?,
        val results: BatchIndexResults
    )

}
