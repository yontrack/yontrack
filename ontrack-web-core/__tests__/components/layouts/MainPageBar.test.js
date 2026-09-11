import "@testing-library/jest-dom"
import {render, screen} from "@testing-library/react"
import MainPageBar from "@components/layouts/MainPageBar"

// `react-dom/server` reaches for `TextEncoder`, which jsdom does not provide.
// Node's own is a drop-in, and it has to be in place before the module is
// loaded - hence the `require` below rather than an import up here.
global.TextEncoder ??= require("util").TextEncoder
global.TextDecoder ??= require("util").TextDecoder
const {renderToString} = require("react-dom/server")

/**
 * The page bar sits directly under the desktop header, and #1729 is about the
 * two of them colliding at phone width. Two things are pinned here, both of
 * which a browser test would only catch by accident:
 *
 * - the breadcrumb is on screen at the *first* render. It used to be parked in
 *   a `useState` filled by a `useEffect`, which rendered an empty bar first;
 * - the row wraps rather than squeezing. Squeezing is what crushed "Home" into
 *   one letter per line on a 393px screen.
 */
describe('MainPageBar', () => {

    it('renders the breadcrumb on the first render, with the title last', () => {
        render(<MainPageBar breadcrumbs={["Home", "Projects"]} title="My project"/>)
        // No `waitFor`: nothing here is asynchronous, and a bar that needs one
        // is a bar that rendered empty first.
        expect(screen.getByText("Home")).toBeInTheDocument()
        expect(screen.getByText("Projects")).toBeInTheDocument()
        expect(screen.getByText("My project")).toBeInTheDocument()
    })

    it('renders the breadcrumb server-side, where effects never run', () => {
        // The assertion that actually pins the derived-state rule. `render()`
        // flushes effects inside `act`, so it passes either way; server
        // rendering does not run them at all, and the desktop UI is a Pages
        // Router app - this markup is what a browser receives first.
        const html = renderToString(
            <MainPageBar breadcrumbs={["Home", "Projects"]} title="My project"/>)
        expect(html).toContain("Home")
        expect(html).toContain("Projects")
        expect(html).toContain("My project")
    })

    it('lets the commands wrap onto their own line instead of squeezing the breadcrumb', () => {
        render(<MainPageBar
            breadcrumbs={["Home"]}
            title="My project"
            commands={[<button key="a">New project</button>]}
        />)
        const row = screen.getByText("New project").closest('div[style*="flex"]')
        expect(row).toHaveStyle({flexWrap: 'wrap'})
    })

    it('renders the description when there is one, and nothing when there is not', () => {
        const {rerender} = render(
            <MainPageBar breadcrumbs={[]} title="My project" description="What it is"/>)
        expect(screen.getByText("What it is")).toBeInTheDocument()

        rerender(<MainPageBar breadcrumbs={[]} title="My project"/>)
        expect(screen.queryByText("What it is")).not.toBeInTheDocument()
    })
})
