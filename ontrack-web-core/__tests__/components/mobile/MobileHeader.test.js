import "@testing-library/jest-dom"
import {render, screen} from "@testing-library/react"
import {UserContext} from "@components/providers/UserProvider"
import MobileHeader from "@components/mobile/layout/MobileHeader"

const renderAs = (user) => render(
    <UserContext.Provider value={user}>
        <MobileHeader/>
    </UserContext.Provider>
)

describe('the mobile header', () => {

    it('carries the Yontrack logo, not only the word', () => {
        renderAs({})
        const logo = screen.getByTestId('mobile-logo')
        expect(logo).toBeInTheDocument()
        expect(logo.getAttribute('src')).toContain('yontrack-logo.svg')
    })

    it('carries the drawn wordmark, not the name set in the UI font', () => {
        // The wordmark is a typeface in brand lilac; typing "Yontrack" instead
        // loses both the colour and the letterforms.
        renderAs({})
        expect(screen.getByTestId('mobile-wordmark').getAttribute('src')).toContain('yontrack-text.svg')
    })

    it.each([
        // Each mark at its own aspect ratio. Getting this wrong squashes the
        // drawing, and Next says so - which is what the desktop `NavBar` does by
        // putting the 8.08:1 wordmark in a 120x24 box.
        ['mobile-logo', '27', '24'],
        ['mobile-wordmark', '129', '16'],
    ])('draws %s at its own aspect ratio', (testId, width, height) => {
        renderAs({})
        const image = screen.getByTestId(testId)
        expect(image).toHaveAttribute('width', width)
        expect(image).toHaveAttribute('height', height)
    })

    it('says "Yontrack" once, not twice, to a screen reader', () => {
        // Two images, one name: the mark is decorative and only the wordmark
        // carries the alt, or the link announces "Yontrack Yontrack".
        renderAs({})
        expect(screen.getByTestId('mobile-brand')).toHaveAccessibleName('Yontrack')
    })

    it('is the way back to the home screen', () => {
        renderAs({})
        expect(screen.getByTestId('mobile-brand')).toHaveAttribute('href', '/mobile')
    })

    it('names who is signed in', () => {
        renderAs({fullName: "Administrator"})
        expect(screen.getByTestId('mobile-user')).toHaveTextContent('Administrator')
    })

    it('leaves the name out when there is none, rather than an empty slot', () => {
        renderAs({})
        expect(screen.queryByTestId('mobile-user')).not.toBeInTheDocument()
    })

    describe('the signed-in name as a door', () => {

        it('is the way to the account screen', () => {
            // Which is the only way out of the mobile UI's session: a phone
            // session is long-lived, and once the app is installed as a PWA
            // there is no address bar to reach `/api/auth/signout` with.
            renderAs({fullName: "Administrator"})
            expect(screen.getByTestId('mobile-user')).toHaveAttribute('href', '/mobile/account')
        })

        it('says where it goes, not only whose name it carries', () => {
            // "Administrator, link" says nothing about the destination. The
            // visible name is kept inside the label, so the two do not diverge.
            renderAs({fullName: "Administrator"})
            expect(screen.getByTestId('mobile-user')).toHaveAccessibleName(/Administrator/)
            expect(screen.getByTestId('mobile-user')).toHaveAccessibleName(/account/i)
        })

        it('marks itself as a door rather than as a label', () => {
            // 13px at 0.85 opacity with no affordance does not read as tappable.
            renderAs({fullName: "Administrator"})
            expect(screen.getByTestId('mobile-user-chevron')).toBeInTheDocument()
        })

        it('does not announce the chevron beside the name', () => {
            renderAs({fullName: "Administrator"})
            expect(screen.getByTestId('mobile-user-chevron')).toHaveAttribute('aria-hidden', 'true')
        })
    })
})
