package net.nemerosa.ontrack.extension.scm.changelog.assistants

import com.fasterxml.jackson.annotation.JsonIgnore
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.model.exceptions.PropertyValidationException

/**
 * Assisted change of a build: whether the commits since the previous build on its branch were written
 * with assistants (agent kinds), as recognised from their trailers, authors and committers.
 *
 * The build is _assisted_ when [assistants] is not empty.
 *
 * @property basis How the value was obtained
 * @property unknownReason Why the value is unknown, when [basis] is [AssistedChangeBasis.UNKNOWN]
 * @property assistants Distinct names of the assistants, sorted
 * @property assistedCommits Number of commits written with an assistant
 * @property totalCommits Number of commits in the change of the build
 * @property sessionLinks Distinct links to the agent sessions behind the commits, at most [MAX_SESSION_LINKS]
 * @property previousBuildId ID of the build the change log was computed from
 */
@APIDescription("Assisted change of a build: whether the commits since the previous build on its branch were written with assistants (agent kinds). The build is assisted when the list of assistants is not empty.")
data class AssistedChangeProperty(
    @APIDescription("How the value was obtained: COMPUTED by Yontrack from the change log, SET_BY_CI, or UNKNOWN when it could not be computed.")
    val basis: AssistedChangeBasis = AssistedChangeBasis.SET_BY_CI,
    @APIDescription("Why the value is unknown, when the basis is UNKNOWN: no SCM, no previous build with a commit, or the SCM error.")
    val unknownReason: String? = null,
    @APIDescription("Distinct names of the assistants, sorted. The build is assisted when there is at least one.")
    val assistants: List<String> = emptyList(),
    @APIDescription("Number of commits written with an assistant.")
    val assistedCommits: Int = 0,
    @APIDescription("Number of commits in the change of the build.")
    val totalCommits: Int = 0,
    @APIDescription("Distinct links to the agent sessions behind the commits, at most 20.")
    val sessionLinks: List<String> = emptyList(),
    @APIDescription("ID of the build the change log was computed from, if any.")
    val previousBuildId: Int? = null,
) {

    /**
     * The build is assisted when there is at least one assistant.
     */
    @get:JsonIgnore
    val assisted: Boolean get() = assistants.isNotEmpty()

    companion object {

        /**
         * Maximum number of session links kept
         */
        const val MAX_SESSION_LINKS = 20

        /**
         * Reason of an unknown value, when the project has no SCM able to compute change logs
         */
        const val REASON_NO_SCM = "no SCM"

        /**
         * Reason of an unknown value, when no previous build on the branch has a commit
         */
        const val REASON_NO_PREVIOUS_BUILD = "no previous build with a commit"

        /**
         * Prefix of the reason of an unknown value, when the SCM failed
         */
        const val REASON_SCM_ERROR_PREFIX = "SCM error: "

        /**
         * Unknown value.
         *
         * @param reason Why the value is unknown
         * @param previousBuildId ID of the build the change log was to be computed from, if any
         */
        fun unknown(reason: String, previousBuildId: Int? = null) = AssistedChangeProperty(
            basis = AssistedChangeBasis.UNKNOWN,
            unknownReason = reason,
            assistants = emptyList(),
            assistedCommits = 0,
            totalCommits = 0,
            sessionLinks = emptyList(),
            previousBuildId = previousBuildId,
        )

        /**
         * Checks a value and normalises it: assistants trimmed, distinct and sorted; session links
         * distinct and capped; no reason unless unknown.
         *
         * @throws PropertyValidationException If the value is not consistent
         */
        fun validated(value: AssistedChangeProperty): AssistedChangeProperty {
            if (value.assistedCommits < 0) {
                throw PropertyValidationException("The number of assisted commits must not be negative.")
            }
            if (value.totalCommits < 0) {
                throw PropertyValidationException("The total number of commits must not be negative.")
            }
            if (value.assistedCommits > value.totalCommits) {
                throw PropertyValidationException("The number of assisted commits (${value.assistedCommits}) must not exceed the total number of commits (${value.totalCommits}).")
            }
            val assistants = value.assistants.map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
            if (value.basis == AssistedChangeBasis.UNKNOWN && assistants.isNotEmpty()) {
                throw PropertyValidationException("An unknown assisted change cannot have assistants.")
            }
            return value.copy(
                unknownReason = value.unknownReason?.trim()?.takeIf {
                    it.isNotEmpty() && value.basis == AssistedChangeBasis.UNKNOWN
                },
                assistants = assistants,
                sessionLinks = value.sessionLinks
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .take(MAX_SESSION_LINKS),
            )
        }
    }
}

/**
 * How the [assisted change][AssistedChangeProperty] of a build was obtained.
 */
enum class AssistedChangeBasis {
    /**
     * Computed by Yontrack from the change log since the previous build with a commit on the branch.
     */
    COMPUTED,

    /**
     * Set by the CI, which wins over any computation.
     */
    SET_BY_CI,

    /**
     * The computation could not run: see the reason.
     */
    UNKNOWN,
}
