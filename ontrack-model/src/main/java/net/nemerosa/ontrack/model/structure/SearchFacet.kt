package net.nemerosa.ontrack.model.structure

/**
 * Number of results for one type.
 *
 * @property type Type of result
 * @property count Number of results for this type
 */
data class SearchFacet(
    val type: SearchResultType,
    val count: Int,
)
