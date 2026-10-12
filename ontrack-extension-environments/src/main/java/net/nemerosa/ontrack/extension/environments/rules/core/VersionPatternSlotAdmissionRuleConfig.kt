package net.nemerosa.ontrack.extension.environments.rules.core

/**
 * Configuration of the [VersionPatternSlotAdmissionRule].
 *
 * @property includes Regular expressions, one of which the version must match. None means every
 * version.
 * @property excludes Regular expressions, none of which the version must match.
 */
data class VersionPatternSlotAdmissionRuleConfig(
    val includes: List<String> = emptyList(),
    val excludes: List<String> = emptyList(),
)
