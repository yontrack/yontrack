package net.nemerosa.ontrack.extension.scorecard.estates

import net.nemerosa.ontrack.extension.scorecard.engine.MarkerKind

/**
 * Marker an estate names for its delivery readings.
 *
 * An estate which names none reads up to its default marker: the highest-ordered environment where
 * the project owns a slot, else the last promotion level of each branch, as with no estate.
 */
sealed interface EstateMarker {
    val kind: MarkerKind
}

/**
 * The delivery readings measure up to a promotion: the promotion level of the given name, on each
 * branch in scope which has one.
 */
data class EstatePromotionMarker(
    val levelName: String,
) : EstateMarker {
    override val kind: MarkerKind = MarkerKind.PROMOTION
}

/**
 * The delivery readings measure up to an environment: the deployments done in the slot of the
 * project in this environment, with this qualifier only — pooling qualifiers would count a
 * deployment several times.
 *
 * @property environment Name of the environment
 * @property qualifier Qualifier of the slot, `""` for the default one
 */
data class EstateEnvironmentMarker(
    val environment: String,
    val qualifier: String = "",
) : EstateMarker {
    override val kind: MarkerKind = MarkerKind.ENVIRONMENT
}
