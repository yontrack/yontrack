import "@testing-library/jest-dom"
import {render, screen} from "@testing-library/react"
import {PreferencesContext} from "@components/providers/PreferencesProvider"
import ScmChangeLogContent from "@components/extension/scm/ScmChangeLogContent"

// Captures every set of props GridTable is rendered with, so we can tell
// whether `items` is ever empty while `layout` already describes 5 widgets.
// react-grid-layout only re-derives its internal layout from a changed
// `layout` prop; if `items` starts empty and is filled in a tick later,
// it falls back to its stale (empty) internal state and collapses every
// widget to a default 1x1 slot at (0, 0).
const gridTableCalls = []
jest.mock("../../../../components/grid/GridTable", () => (props) => {
    gridTableCalls.push(props)
    return (
        <div data-testid="grid-table" data-items={props.items.length}>
            {props.items.map(item => <div key={item.id} data-testid={`cell-${item.id}`}/>)}
        </div>
    )
})

jest.mock("../../../../components/extension/scm/ChangeLogBuild", () => () => <div/>)
jest.mock("../../../../components/grid/GridCell", () => () => <div/>)
jest.mock("../../../../components/extension/scm/ChangeLogLinks", () => () => <div/>)
jest.mock("../../../../components/extension/git/GitChangeLogCommits", () => () => <div/>)
jest.mock("../../../../components/extension/issues/ChangeLogIssues", () => () => <div/>)
jest.mock("../../../../components/extension/scm/views/ChangeLogSemantic", () => () => <div/>)
jest.mock("../../../../components/layouts/MainPage", () => ({children}) => <div>{children}</div>)

const mockRouter = {
    pathname: "/scm/changelog",
    query: {},
    replace: jest.fn(),
}

jest.mock("next/router", () => ({
    useRouter: () => ({
        pathname: mockRouter.pathname,
        query: {...mockRouter.query},
        replace: mockRouter.replace,
    }),
}))

const changeLog = {
    buildFrom: {id: 101, name: "5.1.12", creation: {time: "2026-08-01T10:00:00Z"}},
    buildTo: {id: 102, name: "5.2.0", creation: {time: "2026-08-10T10:00:00Z"}},
    diffLink: "https://example.com/diff",
    linkChanges: [],
    commits: [],
}

const renderContent = ({query = {}, preferences = {}, commits = []} = {}) => {
    mockRouter.query = {from: "101", to: "102", ...query}
    render(
        <PreferencesContext.Provider value={{...preferences, setPreferences: jest.fn(), loaded: true}}>
            <ScmChangeLogContent changeLog={{...changeLog, commits}} loading={false} error={null}/>
        </PreferencesContext.Provider>
    )
}

const cells = () => gridTableCalls[gridTableCalls.length - 1].items.map(it => it.id)

beforeEach(() => {
    gridTableCalls.length = 0
})

describe('ScmChangeLogContent', () => {

    it('renders the grid with all its widgets on the very first render, never with an empty item list', () => {
        renderContent()

        // Regression: `items` used to start as `useState([])` and only be
        // filled by a `useEffect`, so GridTable was first mounted with 0
        // items while `layout` already had 5 entries - the exact mismatch
        // that makes react-grid-layout collapse every widget into the
        // top-left corner (issue #1634).
        expect(gridTableCalls.length).toBeGreaterThan(0)
        gridTableCalls.forEach(call => {
            expect(call.items.length).toEqual(call.layout.length)
            expect(call.items.map(it => it.id)).toEqual(call.layout.map(it => it.i))
        })
        expect(gridTableCalls[0].items.length).toEqual(5)
    })

    describe('the classic view', () => {

        it('is what the page shows when nothing else is asked for', () => {
            renderContent()
            expect(cells()).toEqual(['from', 'to', 'links', 'commits', 'issues'])
        })

        it('keeps its commits and issues whatever the semantic options say', () => {
            renderContent({preferences: {changeLogSemanticCommits: false}})
            expect(cells()).toContain('commits')
            expect(cells()).toContain('issues')
            expect(cells()).not.toContain('semantic')
        })

    })

    describe('the semantic view', () => {

        it('replaces the issues cell with the semantic one', () => {
            renderContent({query: {view: 'semantic'}})
            // The boundary builds and the dependency links stay: they answer *which* two builds
            // and *what dependencies moved*, which are change log concerns rather than
            // classic-view ones.
            expect(cells()).toEqual(['from', 'to', 'links', 'semantic'])
            expect(screen.getByTestId('cell-semantic')).toBeInTheDocument()
            expect(screen.queryByTestId('cell-issues')).not.toBeInTheDocument()
        })

        it('shows the commits cell when the commits option is on', () => {
            renderContent({query: {view: 'semantic', commits: 'true'}})
            expect(cells()).toEqual(['from', 'to', 'links', 'commits', 'semantic'])
        })

        it('is selected by the stored preference as well as by the parameter', () => {
            renderContent({preferences: {selectedChangeLogViewKey: 'semantic'}})
            expect(cells()).toContain('semantic')
        })

        it('lays every cell out without leaving a hole in the grid', () => {
            renderContent({query: {view: 'semantic'}})
            const {layout} = gridTableCalls[gridTableCalls.length - 1]
            expect(layout.map(it => it.i)).toEqual(['from', 'to', 'links', 'semantic'])
            // The two boundary cells share the first row; the rest are full width, stacked
            expect(layout.map(it => [it.x, it.y, it.w])).toEqual([
                [0, 0, 6],
                [6, 0, 6],
                [0, 5, 12],
                [0, 12, 12],
            ])
        })

    })

    describe('the assisted commits', () => {

        const commit = (...names) => ({commit: {assistants: names.map(name => ({name, sessionLink: null}))}})

        it('are counted in the header of the change log', () => {
            renderContent({commits: [commit("Claude Code"), commit(), commit()]})
            expect(screen.getByTestId('change-log-assisted-count')).toHaveTextContent('1 of 3 commits assisted')
        })

        it('are counted in the semantic view as well', () => {
            renderContent({query: {view: 'semantic'}, commits: [commit("Claude Code"), commit()]})
            expect(screen.getByTestId('change-log-assisted-count')).toHaveTextContent('1 of 2 commits assisted')
        })

        it('leave the header alone when there is none', () => {
            renderContent({commits: [commit(), commit()]})
            expect(screen.queryByTestId('change-log-assisted-count')).not.toBeInTheDocument()
        })

    })

})
