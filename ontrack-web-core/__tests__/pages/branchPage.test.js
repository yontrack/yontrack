import {render} from "@testing-library/react"
import BranchPage from "../../pages/branch/[id]"

const mockRouter = {
    pathname: "/branch/[id]",
    asPath: "/branch/42",
    query: {id: "42"},
}

jest.mock("next/router", () => ({
    useRouter: () => mockRouter,
}))

jest.mock("../../components/layouts/MainLayout", () => ({children}) => <div>{children}</div>)

// Counts how many times the branch view is mounted, which is what the `key` of the page governs
const mounts = []
jest.mock("../../components/branches/BranchView", () => ({id}) => {
    const {useEffect} = jest.requireActual("react")
    useEffect(() => {
        mounts.push(id)
    }, [])
    return <div data-testid="branch-view">{String(id)}</div>
})

const renderPage = ({asPath, query}) => {
    mockRouter.asPath = asPath
    mockRouter.query = query
    // A fresh element every time: React bails out of re-rendering an identical element reference
    const {rerender} = render(<BranchPage/>)
    return (next) => {
        mockRouter.asPath = next.asPath
        mockRouter.query = next.query
        rerender(<BranchPage/>)
    }
}

describe('BranchPage', () => {

    beforeEach(() => {
        mounts.length = 0
    })

    it('mounts the branch view for the branch in the URL', () => {
        renderPage({asPath: "/branch/42", query: {id: "42"}})
        expect(mounts).toEqual([42])
    })

    it('remounts the branch view when moving to another branch', () => {
        // The branch view loads its branch once, on mount, so moving between branches has to remount it
        const rerender = renderPage({asPath: "/branch/42", query: {id: "42"}})
        rerender({asPath: "/branch/7", query: {id: "7"}})
        expect(mounts).toEqual([42, 7])
    })

    it('keeps the branch view mounted when only the query changes', () => {
        // Selecting a branch content view writes `?view=` back to the URL. Remounting on that would
        // refetch the branch and throw away the state living above the content view — the validation
        // stamp filter the views share — on every switch.
        const rerender = renderPage({asPath: "/branch/42", query: {id: "42"}})
        rerender({asPath: "/branch/42?view=pipeline", query: {id: "42", view: 'pipeline'}})
        expect(mounts).toEqual([42])
    })

    it('keeps the branch view mounted when the build filter permalink is written and cleared', () => {
        const rerender = renderPage({asPath: "/branch/42", query: {id: "42"}})
        rerender({asPath: "/branch/42?buildFilter=%7B%7D", query: {id: "42", buildFilter: '{}'}})
        rerender({asPath: "/branch/42", query: {id: "42"}})
        expect(mounts).toEqual([42])
    })

})
