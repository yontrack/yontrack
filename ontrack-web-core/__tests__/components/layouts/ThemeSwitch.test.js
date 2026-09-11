import "@testing-library/jest-dom"
import {act, render, screen} from "@testing-library/react"
import ThemeSwitch from "@components/layouts/ThemeSwitch"
import ThemeProvider, {useTheme} from "@components/providers/ThemeProvider"
import {PreferencesContext} from "@components/providers/PreferencesProvider"
import {MessageContext} from "@components/providers/MessageProvider"
import {getStoredThemeMode} from "@components/theme/themeStorage"

/**
 * The desktop switch.
 *
 * The dual write it performs - local mirror, then profile, then a warning when
 * only the first landed - now lives in `useThemeModeSetter`, shared with the
 * mobile row, and is covered once there. What is left here is what is the
 * switch's own: the three options, the drawer it must not close, and the id the
 * UI tests locate it by.
 */

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

    it('writes both halves of the persistence, through the shared setter', () => {
        // The mirror for the next first paint, and the profile so the choice
        // follows the user to their other devices - including their phone, which
        // now reads the very same value.
        const {setPreferences} = renderSwitch()
        pick('Dark')
        expect(getStoredThemeMode()).toEqual('dark')
        expect(setPreferences).toHaveBeenCalledWith({themeMode: 'DARK'})
    })

    it('warns when only the local half landed, in the copy both UIs share', async () => {
        // "another browser" was accurate when this was the only switch there
        // was. What a user loses now is the choice following them to their
        // phone, and the mobile row raises the very same warning.
        const {messageApi} = renderSwitch({
            setPreferences: jest.fn(() => Promise.reject(new Error('backend down'))),
        })
        pick('Dark')
        await act(async () => {
        })
        expect(messageApi.warning).toHaveBeenCalledWith(
            "Theme applied, but not saved to your profile — it may not follow you to other devices."
        )
        // And the choice stands: reverting a theme under the user mid-click
        // would be worse.
        expect(resolved()).toEqual('dark')
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
