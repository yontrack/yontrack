package net.nemerosa.ontrack.model.structure

/**
 * Number of results for one type.
 *
 * @property type Type of result
 * @property count Number of results for this type, at most the cap of the counts
 * @property capped `true` when this type has more results than [count], its cap
 */
data class SearchFacet(
    val type: SearchResultType,
    val count: Int,
    val capped: Boolean = false,
)
