import "@testing-library/jest-dom"
import {act, render, screen} from "@testing-library/react"
import ThemeSwitch from "@components/layouts/ThemeSwitch"
import ThemeProvider, {useTheme} from "@components/providers/ThemeProvider"
import {PreferencesContext} from "@components/providers/PreferencesProvider"
import {MessageContext} from "@components/providers/MessageProvider"
import {getStoredThemeMode} from "@components/theme/themeStorage"

const stubMatchMedia = (dark) => {
    window.matchMedia = jest.fn().mockImplementation(query => ({
        media: query,
        matches: dark,
        addEventListener: () => {
        },
        removeEventListener: () => {
        },
    }))
}

const clearCookies = () => {
    document.cookie.split(';').forEach(c => {
        const name = c.split('=')[0].trim()
        if (name) document.cookie = `${name}=; max-age=0; path=/`
    })
}

function Probe() {
    const {resolvedTheme} = useTheme()
    return <span data-testid="resolved">{resolvedTheme}</span>
}

const renderSwitch = ({setPreferences = jest.fn(), messageApi = {warning: jest.fn()}} = {}) => {
    render(
        <ThemeProvider>
            <MessageContext.Provider value={{messageApi}}>
                <PreferencesContext.Provider value={{setPreferences, loaded: true}}>
                    <ThemeSwitch/>
                    <Probe/>
                </PreferencesContext.Provider>
            </MessageContext.Provider>
        </ThemeProvider>
    )
    return {setPreferences, messageApi}
}

// The Segmented options are radios; each carries its label as accessible name.
const pick = (label) => act(() => screen.getByRole('radio', {name: label}).click())
const resolved = () => screen.getByTestId('resolved').textContent

describe('ThemeSwitch', () => {

    beforeEach(() => {
        clearCookies()
        document.documentElement.removeAttribute('data-theme')
        stubMatchMedia(false)
    })

    afterEach(clearCookies)

    it('offers light, dark and system', () => {
        renderSwitch()
        expect(screen.getByRole('radio', {name: 'Light'})).toBeInTheDocument()
        expect(screen.getByRole('radio', {name: 'Dark'})).toBeInTheDocument()
        expect(screen.getByRole('radio', {name: 'Auto'})).toBeInTheDocument()
    })

    it('shows the current mode as selected', () => {
        renderSwitch()
        expect(screen.getByRole('radio', {name: 'Auto'})).toBeChecked()
    })

    it('applies the choice immediately, without a reload', () => {
        renderSwitch()
        expect(resolved()).toEqual('light')
        pick('Dark')
        expect(resolved()).toEqual('dark')
        expect(document.documentElement.getAttribute('data-theme')).toEqual('dark')
    })

    it('mirrors the choice locally, so the next first paint does not flash', () => {
        renderSwitch()
        pick('Dark')
        expect(getStoredThemeMode()).toEqual('dark')
    })

    it('persists the choice server-side, so it follows the user across browsers', () => {
        const {setPreferences} = renderSwitch()
        pick('Dark')
        // Uppercase: the GraphQL `ThemeMode` enum form.
        expect(setPreferences).toHaveBeenCalledWith({themeMode: 'DARK'})
    })

    it('persists system mode too, rather than treating it as "unset"', () => {
        const {setPreferences} = renderSwitch()
        pick('Dark')
        pick('Auto')
        expect(setPreferences).toHaveBeenLastCalledWith({themeMode: 'SYSTEM'})
        expect(getStoredThemeMode()).toEqual('system')
    })

    describe('when the server rejects the change', () => {

        const failing = () => Promise.reject(new Error('backend down'))

        it('keeps the theme the user just picked rather than snapping it back', async () => {
            renderSwitch({setPreferences: jest.fn(failing)})
            pick('Dark')
            await act(async () => {
            })
            expect(resolved()).toEqual('dark')
        })

        it('says so, instead of failing silently', async () => {
            // The cookie now disagrees with the server: on the next sign-in the
            // server wins and the choice vanishes. The user has to be told.
            const {messageApi} = renderSwitch({setPreferences: jest.fn(failing)})
            pick('Dark')
            await act(async () => {
            })
            expect(messageApi.warning).toHaveBeenCalled()
        })

        it('does not leave an unhandled rejection behind', async () => {
            const onUnhandled = jest.fn()
            process.on('unhandledRejection', onUnhandled)
            try {
                renderSwitch({setPreferences: jest.fn(failing)})
                pick('Dark')
                await act(async () => {
                })
                // A macrotask: long enough for an unhandled rejection to surface.
                await new Promise(resolve => setTimeout(resolve, 0))
                expect(onUnhandled).not.toHaveBeenCalled()
            } finally {
                process.off('unhandledRejection', onUnhandled)
            }
        })
    })

    it('does not let the click reach the surrounding menu', () => {
        // The switch lives in a Menu item inside the user drawer, and that Menu's
        // own onClick closes the drawer. Containing the click is what lets the
        // user compare the two themes without reopening the menu each time.
        const onParentClick = jest.fn()
        render(
            <ThemeProvider>
                <PreferencesContext.Provider value={{setPreferences: jest.fn(), loaded: true}}>
                    <div onClick={onParentClick}>
                        <ThemeSwitch/>
                    </div>
                </PreferencesContext.Provider>
            </ThemeProvider>
        )
        pick('Dark')
        expect(onParentClick).not.toHaveBeenCalled()
    })

    it('carries a stable id, so the UI tests can drive it', () => {
        const {container} = render(
            <ThemeProvider>
                <PreferencesContext.Provider value={{setPreferences: jest.fn(), loaded: true}}>
                    <ThemeSwitch/>
                </PreferencesContext.Provider>
            </ThemeProvider>
        )
        expect(container.querySelector('#theme-switch')).not.toBeNull()
    })
})
