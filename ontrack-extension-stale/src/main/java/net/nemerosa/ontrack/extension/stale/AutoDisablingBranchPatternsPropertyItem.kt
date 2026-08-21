package net.nemerosa.ontrack.extension.stale

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.model.docs.SelfDocumented

@SelfDocumented
@APIDescription("A set of patterns and their behaviour")
data class AutoDisablingBranchPatternsPropertyItem(
    @APIDescription("List of regular expressions against Ontrack branch names. If none, matches all branches.")
    val includes: List<String> = emptyList(),
    @APIDescription("List of regular expressions against Ontrack branch names. If none, no branch is excluded.")
    val excludes: List<String> = emptyList(),
    @APIDescription("Expected behaviour when matching a branch")
    val mode: AutoDisablingBranchPatternsMode = AutoDisablingBranchPatternsMode.KEEP,
    @APIDescription("When mode == KEEP_LAST, number of matching branches to keep (semantic versioning order).")
    val keepLast: Int = 2,
) {
    fun matches(name: String): Boolean {
        val included = includes.isEmpty() || includes.any { name.matches(it.toRegex()) }
        return if (included) {
            excludes.isEmpty() || excludes.none { name.matches(it.toRegex()) }
        } else {
            false
        }
    }
}
