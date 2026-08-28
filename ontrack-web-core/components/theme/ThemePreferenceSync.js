import {useEffect, useRef} from "react"
import {usePreferences} from "@components/providers/PreferencesProvider"
import {useTheme} from "@components/providers/ThemeProvider"

/**
 * Folds the user's server-side theme preference into the theme provider.
 *
 * The provider sits above the preferences in the tree - it has to, so antd is
 * themed before anything renders - and starts from the cookie mirror. This
 * component lives where the preferences are available and pushes the
 * authoritative value down once it arrives, which is what makes the choice
 * follow the user into a browser that has never seen them.
 *
 * Renders nothing, and only ever acts on the *first* load: after that the user's
 * own switching is authoritative, and re-applying a stale server value would
 * fight it.
 */
export default function ThemePreferenceSync() {

    const {loaded, themeMode: serverThemeMode} = usePreferences()
    const {adoptThemeMode} = useTheme()

    const adopted = useRef(false)

    useEffect(() => {
        if (!loaded || adopted.current) return
        adopted.current = true
        if (serverThemeMode) {
            adoptThemeMode(serverThemeMode)
        }
    }, [loaded, serverThemeMode, adoptThemeMode])

    return null
}
