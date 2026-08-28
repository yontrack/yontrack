package net.nemerosa.ontrack.model.preferences

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import net.nemerosa.ontrack.common.api.APIDescription

/**
 * Representation for the preferences of a user.
 *
 * @property branchViewVsNames Displaying the names of the validation stamps
 * @property branchViewVsGroups Grouping validations per status
 * @property themeMode Light/dark theme selected for the web UI
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@APIDescription("Preferences of a user")
data class Preferences(
    @APIDescription("Branch view VS names")
    var branchViewVsNames: Boolean = DEFAULT_BRANCH_VIEW_OPTION,
    @APIDescription("Branch view VS groups")
    var branchViewVsGroups: Boolean = DEFAULT_BRANCH_VIEW_OPTION,
    @APIDescription("Dashboard selected by default")
    var dashboardUuid: String? = null,
    @APIDescription("Selected branch view")
    var selectedBranchViewKey: String? = null,
    @APIDescription("Theme selected for the web UI")
    var themeMode: ThemeMode = DEFAULT_THEME_MODE,
) {
    companion object {
        const val DEFAULT_BRANCH_VIEW_OPTION = false
        val DEFAULT_THEME_MODE = ThemeMode.SYSTEM
    }
}
