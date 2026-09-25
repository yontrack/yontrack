package net.nemerosa.ontrack.repository.search

import net.nemerosa.ontrack.model.structure.SearchDocument
import java.time.LocalDateTime

/**
 * Storage and querying of the search documents, in the `SEARCH_DOCUMENTS` table.
 */
interface SearchDocumentRepository {

    /**
     * Creates or replaces documents, identified by their type and key.
     *
     * @param documents Documents to write
     * @param indexedAt Time of the write
     */
    fun save(documents: List<SearchDocument>, indexedAt: LocalDateTime)

    /**
     * Deletes a document by type and key.
     */
    fun delete(type: String, key: String)

    /**
     * Deletes the documents of a type written before a given time.
     *
     * @return Number of deleted documents
     */
    fun deleteIndexedBefore(type: String, time: LocalDateTime): Int

    /**
     * Deletes all the documents of a type.
     */
    fun deleteAll(type: String): Int

    /**
     * Searches for documents.
     *
     * @param query Text to look for
     * @param scope Types and access
     * @param offset Index of the first document to return
     * @param size Maximum number of documents to return
     * @param perType When set, returns the best [perType] documents of each type, ignoring
     * [offset] and [size]
     * @return `null` if the query is too short to be searched
     */
    fun search(
        query: String,
        scope: SearchDocumentScope,
        offset: Int,
        size: Int,
        perType: Int?,
    ): SearchDocumentPage?

}
