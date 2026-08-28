package net.nemerosa.ontrack.extension.av.versionrules

import net.nemerosa.ontrack.common.api.APIDescription

/**
 * What the [SemVerVersionRule] does when one of the versions it compares is not a semantic version.
 */
@APIDescription("Behaviour of the semver rule when a version cannot be parsed as a semantic version.")
enum class SemVerUnparseablePolicy {

    @APIDescription("The version change is rejected (default).")
    REJECT,

    @APIDescription("The version change is accepted, without any check.")
    ACCEPT,

}
