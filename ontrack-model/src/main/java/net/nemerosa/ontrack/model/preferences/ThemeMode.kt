package net.nemerosa.ontrack.model.preferences

import net.nemerosa.ontrack.common.api.APIDescription

/**
 * Theme selected by a user for the web UI.
 */
@APIDescription("Theme selected by a user for the web UI")
enum class ThemeMode {

    @APIDescription("Always use the light theme")
    LIGHT,

    @APIDescription("Always use the dark theme")
    DARK,

    @APIDescription("Follow the theme of the operating system")
    SYSTEM,

}
