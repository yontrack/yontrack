package net.nemerosa.ontrack.model.structure

import net.nemerosa.ontrack.model.Ack

/**
 * Service to search based on text
 */
interface SearchService {

    /**
     * Gets the list of types of search
     */
    val searchResultTypes: List<SearchResultType>

    /**
     * Searches across several types of results, ranked together.
     *
     * The results are filtered on what the current user can see: the total, the facets and the
     * pages only count what the user can see.
     */
    fun search(request: SearchQueryRequest): SearchResults

    /**
     * Paginated search on one type of results.
     */
    @Deprecated("Use search(SearchQueryRequest). Will be removed in 7.0.")
    fun paginatedSearch(request: SearchRequest): SearchResults =
        search(
            SearchQueryRequest(
                query = request.token,
                types = listOf(request.type),
                offset = request.offset,
                size = request.size,
            )
        )

    /**
     * Deletes the search documents of all types, optionally rebuilding them.
     *
     * This method is mostly used for testing but could be used
     * to repair faulty search documents.
     *
     * @param reindex `true` to rebuild the search documents afterward
     * @param logErrors `true` to log errors only, not raise exceptions
     * @return OK if the reset was completed successfully
     */
    fun indexReset(reindex: Boolean, logErrors: Boolean): Ack

    /**
     * Rebuilds the search documents of a given result type. Waits until the rebuild is completed.
     *
     * @param resultType Result type to rebuild
     * @throws SearchResultTypeNotFoundException If no indexer serves this type
     */
    fun reindex(resultType: String)

}
