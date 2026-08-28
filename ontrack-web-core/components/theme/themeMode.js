/**
 * Theme mode vocabulary, shared by the provider, the storage wrapper and the
 * pre-paint script in `_document.js`.
 *
 * Three modes are supported: `light`, `dark`, and `system` (follow
 * `prefers-color-scheme`). `system` is the default for new users.
 *
 * The mode is what the *user picked*; the resolved theme is what is actually
 * rendered. They differ only in `system` mode.
 */

/** The modes a user can pick from. */
export const THEME_MODES = ['light', 'dark', 'system']

/** Default for a user who has never picked. */
export const DEFAULT_THEME_MODE = 'system'

/**
 * Name of the cookie mirroring the mode.
 *
 * A cookie rather than local storage: it is readable synchronously by the
 * pre-paint script in `_document.js`, before React runs, which is what keeps
 * the first paint from flashing the wrong theme. The server-side preference
 * arrives far too late for that.
 *
 * Kept in sync with the literal in `themeInitScript`, which cannot import.
 */
export const THEME_COOKIE_NAME = 'yontrack-theme'

/**
 * Coerces anything into a usable mode.
 *
 * Accepts the uppercase form too, so the GraphQL `ThemeMode` enum value coming
 * back from the server can be fed in directly.
 *
 * @param {unknown} value
 * @returns {'light'|'dark'|'system'}
 */
export function normalizeThemeMode(value) {
    if (typeof value !== 'string') return DEFAULT_THEME_MODE
    const mode = value.toLowerCase()
    return THEME_MODES.includes(mode) ? mode : DEFAULT_THEME_MODE
}

/**
 * Converts a mode into the value expected by the GraphQL `ThemeMode` enum.
 *
 * @param {unknown} value
 * @returns {string}
 */
export function toServerThemeMode(value) {
    return normalizeThemeMode(value).toUpperCase()
}

/**
 * Resolves the mode into the theme actually rendered.
 *
 * @param {unknown} mode
 * @param {boolean|undefined} prefersDark Whether the system prefers dark.
 *   `undefined` when unknown, which is the case during server rendering.
 * @returns {'light'|'dark'}
 */
export function resolveTheme(mode, prefersDark) {
    const normalized = normalizeThemeMode(mode)
    if (normalized === 'system') {
        return prefersDark ? 'dark' : 'light'
    }
    return normalized
}
