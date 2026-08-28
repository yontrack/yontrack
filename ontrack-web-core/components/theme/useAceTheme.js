import {useTheme} from "@components/providers/ThemeProvider"

/**
 * The Ace editor theme matching the active application theme.
 *
 * Ace ships its own themes and knows nothing about antd's algorithm, so the
 * choice has to be made explicitly. `github_dark` is the counterpart of the
 * `github` theme the editors already used, so the syntax colours stay
 * recognisable across the switch.
 *
 * Both themes are imported statically in `_app.js` - Ace resolves a theme by
 * name at render time and cannot fetch one on demand under the app's bundling.
 *
 * @returns {'github'|'github_dark'}
 */
export function useAceTheme() {
    const {isDark} = useTheme()
    return isDark ? 'github_dark' : 'github'
}
