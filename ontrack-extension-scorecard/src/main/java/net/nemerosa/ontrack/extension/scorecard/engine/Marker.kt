package net.nemerosa.ontrack.extension.scorecard.engine

import net.nemerosa.ontrack.model.structure.PromotionLevel

/**
 * Kind of [marker][Marker], recorded in the `markerKind` detail of every reading.
 */
enum class MarkerKind {
    PROMOTION,
}

/**
 * The event a delivery reading measures up to, resolved for one project.
 *
 * A delivery computer switches on the kind of marker to pick its samples: adding a kind of marker
 * means adding a case to each of them.
 */
sealed interface Marker {

    val kind: MarkerKind

    /**
     * What the marker is, for the `marker` detail of the readings
     */
    val details: Map<String, Any?>
}

/**
 * Promotion marker: one promotion level per branch, samples pooled across the branches.
 *
 * With no estate, each branch in scope is read up to its last promotion level.
 *
 * @property levels Promotion levels, at most one per branch, never empty
 */
data class PromotionMarker(
    val levels: List<PromotionLevel>,
) : Marker {

    init {
        require(levels.isNotEmpty()) { "A promotion marker needs one level at least." }
    }

    override val kind: MarkerKind = MarkerKind.PROMOTION

    override val details: Map<String, Any?>
        get() = mapOf(
            "levels" to levels.associate { it.branch.name to it.name }
        )
}
