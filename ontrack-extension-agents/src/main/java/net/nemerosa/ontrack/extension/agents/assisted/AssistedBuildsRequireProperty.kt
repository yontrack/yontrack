package net.nemerosa.ontrack.extension.agents.assisted

import net.nemerosa.ontrack.common.api.APIDescription

/**
 * *Assisted builds require*, on a promotion level: if the build is assisted, the listed validation
 * stamps must have passed before the build is promoted to the level.
 *
 * @property validationStamps Names of the validation stamps of the level's branch
 */
@APIDescription("If the build is assisted (its commits were written with coding agents), the listed validation stamps must have passed before it is promoted to this level. An unknown or not yet computed assisted change counts as assisted.")
data class AssistedBuildsRequireProperty(
    @APIDescription("Names of the validation stamps of the branch which an assisted build must pass before being promoted to this level.")
    val validationStamps: List<String> = emptyList(),
) {
    companion object {
        /**
         * Normalises a value: names trimmed, without blanks nor duplicates, in their order.
         */
        fun normalised(value: AssistedBuildsRequireProperty) = AssistedBuildsRequireProperty(
            validationStamps = value.validationStamps
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
        )
    }
}
