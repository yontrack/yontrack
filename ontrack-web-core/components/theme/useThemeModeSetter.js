"use client"

/**
 * The one place a theme choice is written.
 *
 * The theme has two halves of persistence and both have to move together:
 *
 *   - the cookie mirror the provider keeps, which is what the pre-paint script
 *     reads so the *next* first paint is already right;
 *   - the `themeMode` preference on the account, which is what makes the choice
 *     follow the user - to another browser, and to their phone.
 *
 * There is one `themeMode` and it is a **user** preference, not a device one:
 * choosing Dark on the phone darkens that account's desktop UI everywhere. The
 * two halves are therefore precisely the thing that must never drift between
 * the two UIs, so the desktop `ThemeSwitch` and the mobile row call this rather
 * than each writing their own pair.
 *
 * It is a hook rather than a service because both halves are only reachable from
 * inside the tree: the theme provider sits *above* the preferences, so it cannot
 * persist to the server itself, and this is the level where both are in scope.
 */

import {useCallback} from "react"
import {useMessageApi} from "@components/providers/MessageProvider"
import {usePreferences} from "@components/providers/PreferencesProvider"
import {useTheme} from "@components/providers/ThemeProvider"
import {toServerThemeMode} from "@components/theme/themeMode"

/**
 * What the user is told when the profile write fails.
 *
 * "other devices" rather than "another browser": with one shared preference,
 * what a user loses is the choice following them to their phone.
 */
export const THEME_NOT_SAVED_WARNING =
    "Theme applied, but not saved to your profile — it may not follow you to other devices."

/**
 * @returns {(mode: 'light'|'dark'|'system') => Promise<void>} Applies a mode and
 *   persists it. Never rejects: the caller is a click handler.
 */
export function useThemeModeSetter() {

    const {setThemeMode} = useTheme()
    const preferences = usePreferences()
    const messageApi = useMessageApi()

    return useCallback(async (mode) => {
        // Applied locally first: the control must take effect immediately, not
        // after the mutation comes back.
        setThemeMode(mode)
        try {
            await preferences.setPreferences({themeMode: toServerThemeMode(mode)})
        } catch (error) {
            // The local choice stands - reverting it under the user mid-tap
            // would be worse. But it must not fail silently: the cookie now
            // disagrees with the server, and on the next sign-in the server wins
            // and the choice quietly disappears.
            messageApi?.warning(THEME_NOT_SAVED_WARNING)
        }
    }, [setThemeMode, preferences, messageApi])
}
