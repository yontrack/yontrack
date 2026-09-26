package net.nemerosa.ontrack.repository.search

import net.nemerosa.ontrack.model.structure.ProjectEntityID
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
     * Creates the documents which do not exist yet, identified by their type and key. The existing
     * ones are left untouched, including their time of write.
     *
     * @param documents Documents to write
     * @param indexedAt Time of the write
     * @return Number of documents created
     */
    fun insertIfAbsent(documents: List<SearchDocument>, indexedAt: LocalDateTime): Int

    /**
     * Deletes a document by type and key.
     */
    fun delete(type: String, key: String)

    /**
     * Deletes the documents of every type describing an entity which is being deleted: the
     * documents of this entity and, for a branch, those of its builds.
     *
     * Must be called before the entity is deleted.
     *
     * @return Number of deleted documents
     */
    fun deleteForEntity(entity: ProjectEntityID): Int

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
     * For each type, at most [countCap] matching documents are counted and ranked: the strongest
     * tiers first, then the most recently updated. The trigram tier is a fallback: it runs for a
     * type only when it has fewer than [SIMILARITY_FALLBACK_THRESHOLD] matches in the other tiers.
     *
     * @param query Text to look for
     * @param scope Types and access
     * @param offset Index of the first document to return
     * @param size Maximum number of documents to return
     * @param perType When set, returns the best [perType] documents of each type, ignoring
     * [offset] and [size]
     * @param highlight `true` to [highlight][SearchDocumentHit.highlight] the free text of the
     * documents returned - of these only, `ts_headline` being expensive
     * @param countCap Maximum number of documents counted, and ranked, for each type
     * @return `null` if the query is too short to be searched
     */
    fun search(
        query: String,
        scope: SearchDocumentScope,
        offset: Int,
        size: Int,
        perType: Int?,
        highlight: Boolean = false,
        countCap: Int = DEFAULT_COUNT_CAP,
    ): SearchDocumentPage?

    /**
     * Sets the `work_mem` of Postgres until the end of the current transaction (`SET LOCAL`).
     * Outside of a transaction, it has no effect.
     */
    fun setLocalWorkMem(workMem: String)

    companion object {
        /**
         * Default of the cap of the counts
         */
        const val DEFAULT_COUNT_CAP = 1000

        /**
         * A type falls back on the trigram tier when it has fewer matches than this in the other
         * tiers. A constant, independent of the page: the matches and the count of a type are the
         * same on every page, in the facets and in the best results per type.
         */
        const val SIMILARITY_FALLBACK_THRESHOLD = 20
    }

}
