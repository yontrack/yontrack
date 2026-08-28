/**
 * Browser-side mirror of the theme mode.
 *
 * The authoritative value lives in the user's server-side preferences, so the
 * choice follows them across browsers. That value only arrives after a GraphQL
 * round trip though - far too late for the first paint. This cookie is the
 * mirror the pre-paint script in `_document.js` reads to get the theme right
 * before anything is drawn.
 *
 * A cookie rather than local storage, because `document.cookie` is the only
 * store the pre-paint script can read without waiting for anything.
 */
import {getCookie, setCookie} from "cookies-next"
import {DEFAULT_THEME_MODE, normalizeThemeMode, THEME_COOKIE_NAME} from "@components/theme/themeMode"

// A year: the mirror should outlive the session, and it is refreshed on every
// change anyway.
const MAX_AGE = 365 * 24 * 3600

/**
 * Reads the mirrored mode.
 *
 * Always returns a usable mode - the default when nothing is stored, or when
 * the stored value is not one we know.
 *
 * @returns {'light'|'dark'|'system'}
 */
export function getStoredThemeMode() {
    if (typeof document === 'undefined') return DEFAULT_THEME_MODE
    return normalizeThemeMode(getCookie(THEME_COOKIE_NAME))
}

/**
 * Mirrors the mode for the next first paint.
 *
 * Normalized on the way in, so the cookie the pre-paint script reads can only
 * ever hold a value that script understands.
 *
 * @param {unknown} mode
 */
export function setStoredThemeMode(mode) {
    if (typeof document === 'undefined') return
    setCookie(THEME_COOKIE_NAME, normalizeThemeMode(mode), {
        path: '/',
        maxAge: MAX_AGE,
        sameSite: 'lax',
    })
}
