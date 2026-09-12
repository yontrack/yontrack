import "@testing-library/jest-dom"
import {act, render, screen} from "@testing-library/react"
import {UserContext} from "@components/providers/UserProvider"
import {RefDataContext} from "@components/providers/RefDataProvider"

const signOut = jest.fn()
jest.mock("next-auth/react", () => ({
    signOut: (...args) => signOut(...args),
}))

/*
 * The module, rather than `window.location`: the screen's contract is that the
 * switch goes *through* `switchToDesktopUI`, which is where the
 * remember-then-navigate ordering lives and which has its own coverage in
 * `desktopPreference.test.js`. Mocking it here asserts the seam instead of
 * re-testing what is behind it - and jsdom refuses to navigate anyway.
 */
const switchToDesktopUI = jest.fn()
jest.mock("../../../components/mobile/desktopPreference", () => ({
    switchToDesktopUI: (...args) => switchToDesktopUI(...args),
}))

import MobileAccountScreen from "@/app/mobile/account/AccountScreen"

const renderScreen = ({user = {}, version = '5.4.0'} = {}) => render(
    <UserContext.Provider value={user}>
        <RefDataContext.Provider value={{version}}>
            <MobileAccountScreen/>
        </RefDataContext.Provider>
    </UserContext.Provider>
)

const ADMIN = {name: 'admin', fullName: "Administrator", email: 'admin@example.com'}

beforeEach(() => {
    signOut.mockClear()
    switchToDesktopUI.mockClear()
})

describe('the mobile account screen', () => {

    describe('who is signed in', () => {

        it('heads the screen with the full name', () => {
            renderScreen({user: ADMIN})
            expect(screen.getByTestId('mobile-screen-title')).toHaveTextContent('Administrator')
        })

        it('carries the username under it', () => {
            // The full name is what a human calls them; the username is what
            // Yontrack calls them, and it is the one that appears in an audit
            // trail or a promotion's author.
            renderScreen({user: ADMIN})
            expect(screen.getByTestId('mobile-screen-subtitle')).toHaveTextContent('admin')
        })

        it('falls back to the username when there is no full name', () => {
            renderScreen({user: {name: 'admin'}})
            expect(screen.getByTestId('mobile-screen-title')).toHaveTextContent('admin')
        })

        it('does not say the username twice when it is all there is', () => {
            // An account with no full name would otherwise head the screen with
            // "admin" over "admin", which reads as a rendering bug.
            renderScreen({user: {name: 'admin'}})
            expect(screen.queryByTestId('mobile-screen-subtitle')).not.toBeInTheDocument()
        })

        it('carries the email as context', () => {
            renderScreen({user: ADMIN})
            expect(screen.getByTestId('mobile-account-email')).toHaveTextContent('admin@example.com')
        })

        it('leaves the email out when the account has none, rather than an empty line', () => {
            renderScreen({user: {name: 'admin', fullName: "Administrator"}})
            expect(screen.queryByTestId('mobile-account-email')).not.toBeInTheDocument()
        })

        it('leaves the email out when it is already the username', () => {
            // An OIDC provider that hands the address over as the username -
            // which is what the dev stack does - would otherwise print
            // `admin@example.com` twice, two lines apart, for no gain. The email
            // is there as *context* for the username, and it is not context for
            // itself.
            renderScreen({user: {name: 'admin@example.com', fullName: "Administrator", email: 'admin@example.com'}})
            expect(screen.queryByTestId('mobile-account-email')).not.toBeInTheDocument()
            expect(screen.getByTestId('mobile-screen-subtitle')).toHaveTextContent('admin@example.com')
        })
    })

    describe('the version', () => {

        it('is on the screen', () => {
            // The PWA has no address bar and no user menu, and "what version are
            // you on?" is the first question on any support thread.
            renderScreen({version: '5.4.0'})
            expect(screen.getByTestId('mobile-account-version')).toHaveTextContent('5.4.0')
        })

        it('is left out entirely rather than shown empty', () => {
            renderScreen({version: ''})
            expect(screen.queryByTestId('mobile-account-version')).not.toBeInTheDocument()
        })
    })

    describe('appearance', () => {

        it('offers the theme control', () => {
            // The mobile UI has the whole theme machinery mounted and, until
            // this row, no affordance at all: a phone user got whatever the
            // device decided.
            renderScreen({user: ADMIN})
            expect(screen.getByTestId('mobile-theme-switch')).toBeInTheDocument()
        })

        it('puts it between the identity and sign out', () => {
            // Sign out stays the last thing on the screen and the only
            // destructive one. A preference placed after it would sit on the
            // path a thumb travels past.
            renderScreen({user: ADMIN})
            const section = screen.getByTestId('mobile-account-appearance')
            const signOut = screen.getByTestId('mobile-account-sign-out')
            const email = screen.getByTestId('mobile-account-email')
            expect(email.compareDocumentPosition(section))
                .toBe(Node.DOCUMENT_POSITION_FOLLOWING)
            expect(section.compareDocumentPosition(signOut))
                .toBe(Node.DOCUMENT_POSITION_FOLLOWING)
        })
    })

    describe('this device', () => {

        it('offers the way to the desktop version', () => {
            // The whole point of the issue: before this row, the only door out
            // of the mobile UI was the interstitial - reached by following a
            // link to a page the user did not want in the first place.
            renderScreen({user: ADMIN})
            expect(screen.getByTestId('open-desktop-version')).toBeInTheDocument()
        })

        it('goes through the switch, and lands on the desktop home', () => {
            // Through `switchToDesktopUI`, so the cookie is written *before* the
            // navigation - write it after and the middleware bounces the user
            // straight back, with the button looking broken. The desktop home
            // and not a computed target: by the time the user is on the account
            // screen, the screen they came from is gone.
            renderScreen({user: ADMIN})
            act(() => screen.getByTestId('open-desktop-version').click())
            expect(switchToDesktopUI).toHaveBeenCalledWith('/')
        })

        it('says what the choice costs and where the way back is', () => {
            // A phone has no hover and, once the app is installed, no address
            // bar: switching has to read as reversible before it is committed
            // to.
            renderScreen({user: ADMIN})
            const caption = screen.getByTestId('mobile-account-device-caption')
            expect(caption).toHaveTextContent(/until you close your browser/i)
            expect(caption).toHaveTextContent(/Mobile version/)
        })

        it('sits between appearance and sign out', () => {
            // Sign out stays the last control on the screen and the only
            // destructive one: a preference placed after it would sit on the
            // path a thumb travels past. A test rather than a comment.
            renderScreen({user: ADMIN})
            const appearance = screen.getByTestId('mobile-account-appearance')
            const device = screen.getByTestId('mobile-account-device')
            const signOutButton = screen.getByTestId('mobile-account-sign-out')
            expect(appearance.compareDocumentPosition(device))
                .toBe(Node.DOCUMENT_POSITION_FOLLOWING)
            expect(device.compareDocumentPosition(signOutButton))
                .toBe(Node.DOCUMENT_POSITION_FOLLOWING)
        })

        it('does not switch anyone merely for opening the screen', () => {
            // The device choice is a decision, not a side effect of landing here.
            renderScreen({user: ADMIN})
            expect(switchToDesktopUI).not.toHaveBeenCalled()
        })
    })

    describe('signing out', () => {

        it('fires immediately, with no confirmation to get past', () => {
            // Two taps and a whole screen of context is already the
            // confirmation; a modal here is what makes a phone app feel like a
            // form. The desktop does not confirm either.
            renderScreen({user: ADMIN})
            act(() => screen.getByTestId('mobile-account-sign-out').click())
            expect(signOut).toHaveBeenCalledTimes(1)
        })

        it('comes back to the mobile home, not to the screen they signed out of', () => {
            // `signOut()` with no argument defaults `callbackUrl` to the current
            // URL, so signing out of `/mobile/build/12` would leave that build as
            // the callback and signing back in would return to it - on a shared
            // phone, the wrong souvenir.
            renderScreen({user: ADMIN})
            act(() => screen.getByTestId('mobile-account-sign-out').click())
            expect(signOut).toHaveBeenCalledWith({callbackUrl: '/mobile'})
        })

        it('does not sign anyone out merely for opening the screen', () => {
            renderScreen({user: ADMIN})
            expect(signOut).not.toHaveBeenCalled()
        })
    })

    it('brings nothing else from the desktop user menu', () => {
        // API tokens, groups, administration, configurations, GraphiQL: the
        // desktop user menu is a menu, and this screen is one verb plus who is
        // signed in.
        renderScreen({user: ADMIN})
        expect(screen.queryByText(/token/i)).not.toBeInTheDocument()
        expect(screen.queryByText(/GraphiQL/i)).not.toBeInTheDocument()
    })
})
