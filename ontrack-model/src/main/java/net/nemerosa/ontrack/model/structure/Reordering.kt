package net.nemerosa.ontrack.model.structure

/**
 * New order of a list of items, as the list of their IDs. Input of the reorder operations.
 */
data class Reordering(
    val ids: List<Int>,
)
