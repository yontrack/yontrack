import "@testing-library/jest-dom"
import {DESKTOP_UI_COOKIE_NAME} from "@components/mobile/desktopUiCookie"
import {switchToDesktopUI, switchToMobileUI} from "@components/mobile/desktopPreference"

const clearCookies = () => {
    document.cookie.split(';').forEach(c => {
        const name = c.split('=')[0].trim()
        if (name) document.cookie = `${name}=; max-age=0; path=/`
    })
}

const assign = jest.fn()

beforeAll(() => {
    // jsdom refuses to navigate, and will not let `assign` be spied on either,
    // so the whole `location` is replaced for the duration of the suite.
    Object.defineProperty(window, 'location', {
        configurable: true,
        value: {...window.location, assign},
    })
})

beforeEach(() => {
    assign.mockClear()
    clearCookies()
})

afterEach(clearCookies)

describe('switching this device between the two UIs', () => {

    it('remembers the desktop choice before navigating', () => {
        // Order, not decoration: the proxy reads the cookie on the very
        // request this navigation makes.
        switchToDesktopUI('/build/56')
        expect(document.cookie).toContain(`${DESKTOP_UI_COOKIE_NAME}=desktop`)
        expect(assign).toHaveBeenCalledWith('/build/56')
    })

    it('goes to the page asked for, not to the desktop home', () => {
        switchToDesktopUI('/search?q=x')
        expect(assign).toHaveBeenCalledWith('/search?q=x')
    })

    it('gives the choice up again on the way back', () => {
        switchToDesktopUI('/build/56')
        switchToMobileUI()
        expect(document.cookie).not.toContain(DESKTOP_UI_COOKIE_NAME)
        expect(assign).toHaveBeenLastCalledWith('/mobile')
    })
})
