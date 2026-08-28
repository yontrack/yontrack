import "@testing-library/jest-dom"
import {act, render, screen} from "@testing-library/react"
import ThemeProvider, {useTheme} from "@components/providers/ThemeProvider"
import {setStoredThemeMode} from "@components/theme/themeStorage"
import {getStoredThemeMode} from "@components/theme/themeStorage"
import {THEME_COOKIE_NAME} from "@components/theme/themeMode"

/**
 * jsdom ships no `matchMedia`. This stands in for it, and lets a test flip the
 * OS preference the way a user changing their system theme would.
 */
const installMatchMedia = (initialDark) => {
    let matches = initialDark
    const listeners = new Set()
    window.matchMedia = jest.fn().mockImplementation(query => ({
        media: query,
        get matches() {
            return matches
        },
        addEventListener: (_, listener) => listeners.add(listener),
        removeEventListener: (_, listener) => listeners.delete(listener),
        // Deprecated API, still what some browsers expose.
        addListener: (listener) => listeners.add(listener),
        removeListener: (listener) => listeners.delete(listener),
    }))
    return {
        set: (dark) => {
            matches = dark
            act(() => {
                listeners.forEach(l => l({matches: dark}))
            })
        },
        listenerCount: () => listeners.size,
    }
}

function Probe() {
    const {themeMode, resolvedTheme, isDark, setThemeMode} = useTheme()
    return (
        <div>
            <span data-testid="mode">{themeMode}</span>
            <span data-testid="resolved">{resolvedTheme}</span>
            <span data-testid="isDark">{String(isDark)}</span>
            <button onClick={() => setThemeMode('dark')}>go dark</button>
            <button onClick={() => setThemeMode('system')}>go system</button>
        </div>
    )
}

const renderProvider = () => render(<ThemeProvider><Probe/></ThemeProvider>)

const clearCookies = () => {
    document.cookie.split(';').forEach(c => {
        const name = c.split('=')[0].trim()
        if (name) document.cookie = `${name}=; max-age=0; path=/`
    })
}

const mode = () => screen.getByTestId('mode').textContent
const resolved = () => screen.getByTestId('resolved').textContent
const rootTheme = () => document.documentElement.getAttribute('data-theme')

describe('ThemeProvider', () => {

    beforeEach(() => {
        clearCookies()
        document.documentElement.removeAttribute('data-theme')
        document.documentElement.style.colorScheme = ''
    })

    afterEach(clearCookies)

    describe('initial mode', () => {

        it('follows the system by default, for a user who has never picked', () => {
            installMatchMedia(false)
            renderProvider()
            expect(mode()).toEqual('system')
        })

        it('picks up the mirrored choice without waiting for the server', () => {
            installMatchMedia(false)
            setStoredThemeMode('dark')
            renderProvider()
            // Read synchronously on the very first render - anything later is a
            // flash of the wrong theme.
            expect(mode()).toEqual('dark')
            expect(resolved()).toEqual('dark')
        })
    })

    describe('system mode', () => {

        it('resolves to dark when the OS prefers dark', () => {
            installMatchMedia(true)
            renderProvider()
            expect(resolved()).toEqual('dark')
            expect(screen.getByTestId('isDark').textContent).toEqual('true')
        })

        it('resolves to light when the OS prefers light', () => {
            installMatchMedia(false)
            renderProvider()
            expect(resolved()).toEqual('light')
        })

        it('reacts to the OS flipping its theme while the page is open', () => {
            const media = installMatchMedia(false)
            renderProvider()
            expect(resolved()).toEqual('light')
            media.set(true)
            expect(resolved()).toEqual('dark')
        })

        it('stops listening to the OS once unmounted', () => {
            const media = installMatchMedia(false)
            const {unmount} = renderProvider()
            expect(media.listenerCount()).toBeGreaterThan(0)
            unmount()
            expect(media.listenerCount()).toEqual(0)
        })
    })

    describe('an explicit mode wins over the OS', () => {

        it('stays dark even when the OS prefers light', () => {
            installMatchMedia(false)
            setStoredThemeMode('dark')
            renderProvider()
            expect(resolved()).toEqual('dark')
        })

        it('ignores the OS flipping while an explicit mode is set', () => {
            const media = installMatchMedia(false)
            setStoredThemeMode('light')
            renderProvider()
            media.set(true)
            expect(resolved()).toEqual('light')
        })
    })

    describe('setThemeMode', () => {

        it('takes effect immediately, with no reload', () => {
            installMatchMedia(false)
            renderProvider()
            expect(resolved()).toEqual('light')
            act(() => screen.getByText('go dark').click())
            expect(mode()).toEqual('dark')
            expect(resolved()).toEqual('dark')
        })

        it('mirrors the choice so the next first paint is already right', () => {
            installMatchMedia(false)
            renderProvider()
            act(() => screen.getByText('go dark').click())
            expect(getStoredThemeMode()).toEqual('dark')
            expect(document.cookie).toContain(`${THEME_COOKIE_NAME}=dark`)
        })

        it('hands control back to the OS when switching to system', () => {
            const media = installMatchMedia(true)
            setStoredThemeMode('light')
            renderProvider()
            expect(resolved()).toEqual('light')
            act(() => screen.getByText('go system').click())
            expect(resolved()).toEqual('dark')
            media.set(false)
            expect(resolved()).toEqual('light')
        })
    })

    describe('the document reflects the resolved theme', () => {

        it('exposes it as data-theme, which is what the CSS custom properties key off', () => {
            installMatchMedia(false)
            renderProvider()
            expect(rootTheme()).toEqual('light')
            act(() => screen.getByText('go dark').click())
            expect(rootTheme()).toEqual('dark')
        })

        it('sets color-scheme, so browser-native UI (scrollbars, form controls) follows too', () => {
            installMatchMedia(false)
            renderProvider()
            act(() => screen.getByText('go dark').click())
            expect(document.documentElement.style.colorScheme).toEqual('dark')
        })
    })

    describe('adoptThemeMode', () => {

        it('applies the server preference without echoing it back as a user choice', () => {
            installMatchMedia(false)

            function Adopter() {
                const {adoptThemeMode, themeMode} = useTheme()
                return <button data-mode={themeMode} onClick={() => adoptThemeMode('DARK')}>adopt</button>
            }

            render(<ThemeProvider><Probe/><Adopter/></ThemeProvider>)
            expect(mode()).toEqual('system')
            act(() => screen.getByText('adopt').click())
            // Accepts the uppercase GraphQL enum form.
            expect(mode()).toEqual('dark')
            expect(resolved()).toEqual('dark')
            // Mirrored too, so a reload does not flash the old theme.
            expect(getStoredThemeMode()).toEqual('dark')
        })
    })
})
