import {themeInitScript} from "@components/theme/themeInitScript"
import {THEME_COOKIE_NAME} from "@components/theme/themeMode"
import {setStoredThemeMode} from "@components/theme/themeStorage"

/**
 * The script is injected inline, so it cannot import - it duplicates the cookie
 * name and the mode vocabulary. These tests run the real script text against a
 * real cookie written by the real storage wrapper, which is what keeps the
 * duplication honest.
 */
const runInitScript = () => {
    // eslint-disable-next-line no-eval
    window.eval(themeInitScript)
}

const stubMatchMedia = (dark) => {
    window.matchMedia = jest.fn().mockImplementation(query => ({media: query, matches: dark}))
}

const clearCookies = () => {
    document.cookie.split(';').forEach(c => {
        const name = c.split('=')[0].trim()
        if (name) document.cookie = `${name}=; max-age=0; path=/`
    })
}

const rootTheme = () => document.documentElement.getAttribute('data-theme')

describe('themeInitScript', () => {

    beforeEach(() => {
        clearCookies()
        document.documentElement.removeAttribute('data-theme')
        document.documentElement.style.colorScheme = ''
        stubMatchMedia(false)
    })

    afterEach(clearCookies)

    it('reads the cookie the storage wrapper actually writes', () => {
        setStoredThemeMode('dark')
        runInitScript()
        expect(rootTheme()).toEqual('dark')
    })

    it('honours an explicit light choice against a dark OS', () => {
        stubMatchMedia(true)
        setStoredThemeMode('light')
        runInitScript()
        expect(rootTheme()).toEqual('light')
    })

    it('follows the OS when no choice has been mirrored yet', () => {
        stubMatchMedia(true)
        runInitScript()
        expect(rootTheme()).toEqual('dark')
    })

    it('follows the OS in system mode', () => {
        stubMatchMedia(true)
        setStoredThemeMode('system')
        runInitScript()
        expect(rootTheme()).toEqual('dark')
    })

    it('sets color-scheme so native controls match from the first paint', () => {
        setStoredThemeMode('dark')
        runInitScript()
        expect(document.documentElement.style.colorScheme).toEqual('dark')
    })

    it('falls back to light rather than leaving the page unthemed', () => {
        document.cookie = `${THEME_COOKIE_NAME}=neon; path=/`
        runInitScript()
        expect(rootTheme()).toEqual('light')
    })

    it('survives a browser with no matchMedia at all', () => {
        delete window.matchMedia
        runInitScript()
        expect(rootTheme()).toEqual('light')
    })

    it('is not confused by another cookie whose name ends with the same text', () => {
        document.cookie = `not-the-${THEME_COOKIE_NAME}=dark; path=/`
        setStoredThemeMode('light')
        runInitScript()
        expect(rootTheme()).toEqual('light')
    })
})
