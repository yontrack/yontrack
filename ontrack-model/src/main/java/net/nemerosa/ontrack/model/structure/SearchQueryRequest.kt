package net.nemerosa.ontrack.model.structure

/**
 * Search across several types of results.
 *
 * @property query Text to look for, at least [MIN_QUERY_LENGTH] characters long
 * @property types IDs of the [types][SearchResultType] to restrict the search to, `null` for all
 * of them
 * @property offset Index of the first result to return
 * @property size Maximum number of results to return
 * @property perType When set, returns the best [perType] results of each type instead of a page:
 * [offset] and [size] are then ignored.
 * @property highlight `true` to compute the [highlight][SearchResult.highlight] of the free text of
 * the results returned - never of the other matches, since `ts_headline` is expensive
 */
data class SearchQueryRequest(
    val query: String,
    val types: List<String>? = null,
    val offset: Int = 0,
    val size: Int = DEFAULT_SIZE,
    val perType: Int? = null,
    val highlight: Boolean = false,
) {
    companion object {
        const val DEFAULT_SIZE = 20

        /**
         * Minimum length of a query, once trimmed. A shorter query returns no result.
         */
        const val MIN_QUERY_LENGTH = 2
    }
}
