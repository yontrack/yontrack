import "@testing-library/jest-dom"
import {act, render, screen} from "@testing-library/react"
import {UserContext} from "@components/providers/UserProvider"
import {RefDataContext} from "@components/providers/RefDataProvider"

const signOut = jest.fn()
jest.mock("next-auth/react", () => ({
    signOut: (...args) => signOut(...args),
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

beforeEach(() => signOut.mockClear())

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
