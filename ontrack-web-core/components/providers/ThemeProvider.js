import {createContext, useCallback, useContext, useEffect, useMemo, useState} from "react"
import {ConfigProvider, theme as antdTheme} from "antd"
import {DEFAULT_THEME_MODE, normalizeThemeMode, resolveTheme} from "@components/theme/themeMode"
import {getStoredThemeMode, setStoredThemeMode} from "@components/theme/themeStorage"

const DARK_MEDIA_QUERY = '(prefers-color-scheme: dark)'

/**
 * Reads the OS preference, synchronously and defensively.
 *
 * Returns `undefined` when there is nothing to read - during server rendering,
 * and under test runners whose DOM has no `matchMedia`.
 *
 * @returns {boolean|undefined}
 */
const readPrefersDark = () => {
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return undefined
    return window.matchMedia(DARK_MEDIA_QUERY).matches
}

export const ThemeContext = createContext({
    /** What the user picked. */
    themeMode: DEFAULT_THEME_MODE,
    /** What is actually rendered - differs from the mode only in `system` mode. */
    resolvedTheme: 'light',
    isDark: false,
    /** Records a user's choice. Callers persist it separately. */
    setThemeMode: () => {
    },
    /** Applies a choice made elsewhere (the server preference). */
    adoptThemeMode: () => {
    },
})

/**
 * Owns the light/dark theme for the whole application, and applies it to antd
 * through `ConfigProvider`.
 *
 * Placed at the very top of the provider stack in `_app.js`, above the message
 * provider, so antd's portalled surfaces (messages, modals, drawers) are themed
 * too.
 *
 * The initial mode is read from the cookie mirror synchronously, on the first
 * render: reading it any later would paint the wrong theme first. The
 * authoritative server-side preference is folded in afterwards by
 * `ThemePreferenceSync`, which lives further down the tree where the
 * preferences are available.
 *
 * Nothing here branches on the theme to produce *markup*. The application's own
 * colours are CSS custom properties keyed off the `data-theme` attribute this
 * provider sets, and antd runs in `cssVar` mode, so switching swaps variable
 * values rather than re-rendering or re-generating styles.
 */
export default function ThemeProvider({children}) {

    // Read on the first render, not in an effect: the mirror exists precisely so
    // the correct theme is known before anything is painted.
    const [themeMode, setThemeModeState] = useState(getStoredThemeMode)
    const [prefersDark, setPrefersDark] = useState(readPrefersDark)

    // `system` must keep tracking the OS while the page is open, not just at load.
    useEffect(() => {
        if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return
        const media = window.matchMedia(DARK_MEDIA_QUERY)
        const onChange = (event) => setPrefersDark(event.matches)
        // `addListener` is the deprecated form, kept for older Safari.
        if (media.addEventListener) {
            media.addEventListener('change', onChange)
            return () => media.removeEventListener('change', onChange)
        } else if (media.addListener) {
            media.addListener(onChange)
            return () => media.removeListener(onChange)
        }
    }, [])

    const resolvedTheme = resolveTheme(themeMode, prefersDark)
    const isDark = resolvedTheme === 'dark'

    // The attribute the CSS custom properties key off. Already set by the
    // pre-paint script in `_document.js`; kept in sync here for later changes.
    useEffect(() => {
        const root = document.documentElement
        root.setAttribute('data-theme', resolvedTheme)
        // Makes browser-native UI - scrollbars, date pickers, form controls -
        // follow the theme as well.
        root.style.colorScheme = resolvedTheme
    }, [resolvedTheme])

    const applyThemeMode = useCallback((mode) => {
        const normalized = normalizeThemeMode(mode)
        setThemeModeState(normalized)
        setStoredThemeMode(normalized)
        return normalized
    }, [])

    const contextValue = useMemo(() => ({
        themeMode,
        resolvedTheme,
        isDark,
        setThemeMode: applyThemeMode,
        adoptThemeMode: applyThemeMode,
    }), [themeMode, resolvedTheme, isDark, applyThemeMode])

    return (
        <ThemeContext.Provider value={contextValue}>
            <ConfigProvider
                theme={{
                    algorithm: isDark ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm,
                    // Emits the design tokens as CSS variables instead of baking
                    // them into generated class names. Switching then costs a
                    // variable swap rather than a full style regeneration - and
                    // it is the default in antd 6, so this pre-stages that upgrade.
                    cssVar: true,
                }}
            >
                {children}
            </ConfigProvider>
        </ThemeContext.Provider>
    )
}

export function useTheme() {
    return useContext(ThemeContext)
}
