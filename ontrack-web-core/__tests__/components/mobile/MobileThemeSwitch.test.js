import "@testing-library/jest-dom"
import {act, render, screen} from "@testing-library/react"
import MobileThemeSwitch from "@components/mobile/account/MobileThemeSwitch"
import ThemeProvider from "@components/providers/ThemeProvider"
import {PreferencesContext} from "@components/providers/PreferencesProvider"
import {MessageContext} from "@components/providers/MessageProvider"
import {getStoredThemeMode} from "@components/theme/themeStorage"

/**
 * The mobile theme row.
 *
 * Deliberately not `ThemeSwitch`: that one is desktop-shaped - a hard-coded
 * `id="theme-switch"` the desktop UI test locates by, tooltips a touch screen
 * cannot reach, and a `stopPropagation` whose only job is keeping the desktop
 * drawer open. What the two share is the write, and that is covered on
 * `useThemeModeSetter` rather than twice here.
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

const renderSwitch = ({setPreferences = jest.fn(), messageApi = {warning: jest.fn()}} = {}) => {
    const rendered = render(
        <ThemeProvider>
            <MessageContext.Provider value={{messageApi}}>
                <PreferencesContext.Provider value={{setPreferences, loaded: true}}>
                    <MobileThemeSwitch/>
                </PreferencesContext.Provider>
            </MessageContext.Provider>
        </ThemeProvider>
    )
    return {...rendered, setPreferences, messageApi}
}

// The Segmented options are radios; each carries its label as accessible name.
const pick = (label) => act(() => screen.getByRole('radio', {name: label}).click())
const caption = () => screen.getByTestId('mobile-theme-caption').textContent

describe('the mobile theme switch', () => {

    beforeEach(() => {
        clearCookies()
        document.documentElement.removeAttribute('data-theme')
        stubMatchMedia(false)
    })

    afterEach(clearCookies)

    it('offers the same three modes as the desktop, not a binary toggle', () => {
        // `system` is the default for everyone who has never picked, so a toggle
        // could express neither the state most users are in nor the way back to
        // it - and the value is shared with the desktop, which offers three.
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
        pick('Dark')
        expect(document.documentElement.getAttribute('data-theme')).toEqual('dark')
    })

    it('mirrors the choice locally and saves it to the profile', () => {
        // One shared `themeMode`: the same value the desktop reads, which is
        // what makes a choice made on the phone darken the desktop too.
        const {setPreferences} = renderSwitch()
        pick('Dark')
        expect(getStoredThemeMode()).toEqual('dark')
        expect(setPreferences).toHaveBeenCalledWith({themeMode: 'DARK'})
    })

    describe('the caption', () => {

        it('says what Light means, rather than leaving it to a tooltip', () => {
            // A phone has no hover, so the only place a mode is ever explained
            // on the desktop is unreachable here.
            renderSwitch()
            pick('Light')
            expect(caption()).toEqual("Always light")
        })

        it('says what Dark means', () => {
            renderSwitch()
            pick('Dark')
            expect(caption()).toEqual("Always dark")
        })

        it('says which theme Auto is currently resolving to', () => {
            // Auto arriving as a monitor icon and four letters describes the
            // mode most users are already in and tells them nothing. The caption
            // is where "Auto" becomes an answer.
            stubMatchMedia(true)
            renderSwitch()
            pick('Auto')
            expect(caption()).toEqual("Auto — currently dark")
        })

        it('says so when Auto resolves to light', () => {
            renderSwitch()
            pick('Auto')
            expect(caption()).toEqual("Auto — currently light")
        })

        it('is there in every mode, so choosing one never moves the rows below', () => {
            // Sign out sits under this row. A caption that appears and
            // disappears changes the row's height as the user taps across it,
            // which puts a destructive button under their thumb as a side effect
            // of choosing a theme.
            renderSwitch()
            for (const mode of ['Light', 'Dark', 'Auto']) {
                pick(mode)
                expect(caption()).not.toEqual('')
            }
        })
    })

    it("carries its own test id, and not the desktop switch's", () => {
        const {container} = renderSwitch()
        expect(screen.getByTestId('mobile-theme-switch')).toBeInTheDocument()
        expect(container.querySelector('#theme-switch')).toBeNull()
    })
})
