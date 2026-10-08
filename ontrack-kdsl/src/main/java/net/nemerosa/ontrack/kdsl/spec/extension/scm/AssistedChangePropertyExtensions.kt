package net.nemerosa.ontrack.kdsl.spec.extension.scm

import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.kdsl.spec.Build
import net.nemerosa.ontrack.kdsl.spec.deleteProperty
import net.nemerosa.ontrack.kdsl.spec.getProperty
import net.nemerosa.ontrack.kdsl.spec.setProperty

const val ASSISTED_CHANGE_PROPERTY =
    "net.nemerosa.ontrack.extension.scm.changelog.assistants.AssistedChangePropertyType"

/**
 * How the assisted change of a build was obtained.
 */
enum class AssistedChangeBasis {
    COMPUTED,
    SET_BY_CI,
    UNKNOWN,
}

/**
 * Assisted change of a build: whether its commits since the previous build on its branch were
 * written with assistants (agent kinds).
 *
 * @property basis How the value was obtained
 * @property unknownReason Why the value is unknown
 * @property assistants Names of the assistants
 * @property assistedCommits Number of commits written with an assistant
 * @property totalCommits Number of commits in the change of the build
 * @property sessionLinks Links to the agent sessions behind the commits
 * @property previousBuildId ID of the build the change log was computed from
 */
data class AssistedChange(
    val basis: AssistedChangeBasis = AssistedChangeBasis.SET_BY_CI,
    val unknownReason: String? = null,
    val assistants: List<String> = emptyList(),
    val assistedCommits: Int = 0,
    val totalCommits: Int = 0,
    val sessionLinks: List<String> = emptyList(),
    val previousBuildId: Int? = null,
)

/**
 * Assisted change of a build. CI sets it with the [SET_BY_CI][AssistedChangeBasis.SET_BY_CI] basis.
 */
var Build.assistedChange: AssistedChange?
    get() = getProperty(ASSISTED_CHANGE_PROPERTY)?.parse()
    set(value) {
        if (value != null) {
            setProperty(ASSISTED_CHANGE_PROPERTY, value)
        } else {
            deleteProperty(ASSISTED_CHANGE_PROPERTY)
        }
    }
