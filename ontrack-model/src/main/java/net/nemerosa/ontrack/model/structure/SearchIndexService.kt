package net.nemerosa.ontrack.model.structure

import co.elastic.clients.elasticsearch._types.query_dsl.Query

/**
 * This service is used to manage the search indexes when they are available.
 */
interface SearchIndexService {

    /**
     * Are search indexes available?
     */
    val searchIndexesAvailable: Boolean

    fun <T : SearchItem> initIndex(indexer: SearchIndexer<T>)

    fun <T : SearchItem> index(indexer: SearchIndexer<T>)

    /**
     * Deletes an index by name
     */
    fun deleteIndex(indexName: String)

    fun <T : SearchItem> resetIndex(indexer: SearchIndexer<T>, reindex: Boolean, logErrors: Boolean): Boolean

    fun <T : SearchItem> createSearchIndex(indexer: SearchIndexer<T>, item: T)

    fun <T : SearchItem> updateSearchIndex(indexer: SearchIndexer<T>, item: T)

    fun <T : SearchItem> deleteSearchIndex(indexer: SearchIndexer<T>, id: String)

    /**
     * Deletes all the documents of an index matching a query.
     *
     * @return Number of deleted documents
     */
    fun <T : SearchItem> deleteSearchIndexByQuery(indexer: SearchIndexer<T>, query: Query): Long

    fun <T : SearchItem> batchSearchIndex(indexer: SearchIndexer<T>, items: Collection<T>, mode: BatchIndexMode): BatchIndexResults

}

/**
 * Batch indexing mode
 */
enum class BatchIndexMode {
    /**
     * If ID already exists, leaves as-is
     */
    KEEP,
    /**
     * If ID already exists, replaces it
     */
    UPDATE
}

/**
 * Results of batch indexing
 */
data class BatchIndexResults(
        val added: Int,
        val updated: Int,
        val kept: Int,
        val deleted: Int
) {
    companion object {
        val NONE = BatchIndexResults(added = 0, updated = 0, kept = 0, deleted = 0)
        val KEEP = BatchIndexResults(added = 0, updated = 0, kept = 1, deleted = 0)
        val UPDATE = BatchIndexResults(added = 0, updated = 1, kept = 0, deleted = 0)
        val ADD = BatchIndexResults(added = 1, updated = 0, kept = 0, deleted = 0)
    }

    operator fun plus(other: BatchIndexResults) =
            BatchIndexResults(
                    added = added + other.added,
                    updated = updated + other.updated,
                    kept = kept + other.kept,
                    deleted = deleted + other.deleted
            )
}

/**
 * Utility extension accepting any kind of ID.
 */
fun <T : SearchItem> SearchIndexService.deleteSearchIndex(indexer: SearchIndexer<T>, id: Any) =
        deleteSearchIndex(indexer, id.toString())
