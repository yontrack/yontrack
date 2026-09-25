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
     * Makes sure all search indexes are initialized.
     */
    fun indexInit()

    /**
     * Resetting all search indexes, optionally restoring them.
     *
     *
     * This method is mostly used for testing but could be used
     * to reset faulty indexes.
     *
     * @param reindex `true` to relaunch the indexation afterward
     * @param logErrors `true` to log errors only, not raise exceptions
     * @return OK if indexation was completed successfully
     */
    fun indexReset(reindex: Boolean, logErrors: Boolean): Ack

    /**
     * Launching the indexation for a given result type. Waits until the indexation is completed.
     *
     * @param resultType Result type to index
     */
    fun reindex(resultType: String)

}
