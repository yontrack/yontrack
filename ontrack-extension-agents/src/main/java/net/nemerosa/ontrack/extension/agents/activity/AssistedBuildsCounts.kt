package net.nemerosa.ontrack.extension.agents.activity

/**
 * Counts of the builds created in a window, by their assisted change.
 *
 * A build whose assisted change is not computed yet is in none of the counts.
 *
 * @property assisted Builds whose assisted change is known and has at least one assistant
 * @property known Builds whose assisted change is known - `COMPUTED` or `SET_BY_CI`
 * @property unknown Builds whose assisted change is `UNKNOWN`
 */
data class AssistedBuildsCounts(
    val assisted: Int,
    val known: Int,
    val unknown: Int,
) {
    companion object {
        /**
         * No build at all
         */
        val NONE = AssistedBuildsCounts(assisted = 0, known = 0, unknown = 0)
    }
}
