package net.nemerosa.ontrack.kdsl.spec.search

import tools.jackson.databind.JsonNode

/**
 * Results of a search across types.
 *
 * @property total Total number of results the user can see
 * @property facets Number of results per type, for the types having some
 * @property items Results, best first
 * @property message Message about the search, for example when the search index is being built
 */
data class SearchResults(
    val total: Int,
    val facets: List<SearchFacet>,
    val items: List<SearchResultItem>,
    val message: String?,
)

/**
 * Number of results for one type.
 */
data class SearchFacet(
    val type: SearchResultType,
    val count: Int,
)

/**
 * One result of a search across types.
 *
 * @property data What the result needs to be rendered and linked
 */
data class SearchResultItem(
    val title: String,
    val description: String,
    val accuracy: Double,
    val type: SearchResultType,
    val data: JsonNode?,
)
