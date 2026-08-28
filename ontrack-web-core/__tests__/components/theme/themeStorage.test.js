import {getStoredThemeMode, setStoredThemeMode} from "@components/theme/themeStorage"
import {DEFAULT_THEME_MODE, THEME_COOKIE_NAME} from "@components/theme/themeMode"

const clearCookies = () => {
    document.cookie.split(';').forEach(c => {
        const name = c.split('=')[0].trim()
        if (name) document.cookie = `${name}=; max-age=0; path=/`
    })
}

describe('theme storage', () => {

    beforeEach(clearCookies)
    afterEach(clearCookies)

    it('returns the default when nothing has been stored', () => {
        expect(getStoredThemeMode()).toEqual(DEFAULT_THEME_MODE)
    })

    it.each(['light', 'dark', 'system'])('round-trips %s', (mode) => {
        setStoredThemeMode(mode)
        expect(getStoredThemeMode()).toEqual(mode)
    })

    it('stores under the name the pre-paint script reads', () => {
        setStoredThemeMode('dark')
        expect(document.cookie).toContain(`${THEME_COOKIE_NAME}=dark`)
    })

    it('normalizes on the way in, so a bad value can never be persisted', () => {
        setStoredThemeMode('PURPLE')
        expect(getStoredThemeMode()).toEqual(DEFAULT_THEME_MODE)
        expect(document.cookie).toContain(`${THEME_COOKIE_NAME}=${DEFAULT_THEME_MODE}`)
    })

    it('normalizes on the way out, so a hand-edited cookie cannot break the UI', () => {
        document.cookie = `${THEME_COOKIE_NAME}=neon; path=/`
        expect(getStoredThemeMode()).toEqual(DEFAULT_THEME_MODE)
    })

    it('accepts the uppercase form the server enum uses', () => {
        setStoredThemeMode('DARK')
        expect(getStoredThemeMode()).toEqual('dark')
    })
})
