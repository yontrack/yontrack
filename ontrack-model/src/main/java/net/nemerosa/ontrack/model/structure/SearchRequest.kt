package net.nemerosa.ontrack.model.structure

/**
 * Search request.
 *
 * @param token Free text for the search
 * @param type Type of search, linked to [SearchResultType.id]
 * @param offset Offset for the results
 * @param size Number of results returned after [offset]
 */
@Deprecated("Use SearchQueryRequest. Will be removed in 7.0.")
class SearchRequest @JvmOverloads constructor(
        val token: String,
        val type: String,
        val offset: Int = 0,
        val size: Int = 10
)
