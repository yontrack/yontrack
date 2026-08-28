package net.nemerosa.ontrack.extension.av.versionrules

import net.nemerosa.ontrack.common.api.APIDescription

/**
 * Configuration of the [SemVerVersionRule].
 *
 * @property onUnparseable What to do when the current or the target version is not a semantic version
 */
@APIDescription("Configuration of the `semver` auto-versioning version rule.")
data class SemVerVersionRuleConfig(
    @APIDescription("What to do when the current or the target version is not a semantic version. Rejects by default.")
    val onUnparseable: SemVerUnparseablePolicy = SemVerUnparseablePolicy.REJECT,
)
