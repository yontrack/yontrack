import "@testing-library/jest-dom"
import {render} from "@testing-library/react"
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
    return <div data-testid="grid-table" data-items={props.items.length}/>
})

jest.mock("../../../../components/extension/scm/ChangeLogBuild", () => () => <div/>)
jest.mock("../../../../components/grid/GridCell", () => () => <div/>)
jest.mock("../../../../components/extension/scm/ChangeLogLinks", () => () => <div/>)
jest.mock("../../../../components/extension/git/GitChangeLogCommits", () => () => <div/>)
jest.mock("../../../../components/extension/issues/ChangeLogIssues", () => () => <div/>)
jest.mock("../../../../components/layouts/MainPage", () => ({children}) => <div>{children}</div>)

const changeLog = {
    buildFrom: {id: 101, name: "5.1.12", creation: {time: "2026-08-01T10:00:00Z"}},
    buildTo: {id: 102, name: "5.2.0", creation: {time: "2026-08-10T10:00:00Z"}},
    diffLink: "https://example.com/diff",
    linkChanges: [],
    commits: [],
}

beforeEach(() => {
    gridTableCalls.length = 0
})

describe('ScmChangeLogContent', () => {

    it('renders the grid with all its widgets on the very first render, never with an empty item list', () => {
        render(<ScmChangeLogContent changeLog={changeLog} loading={false} error={null}/>)

        // Regression: `items` used to start as `useState([])` and only be
        // filled by a `useEffect`, so GridTable was first mounted with 0
        // items while `layout` already had 5 entries - the exact mismatch
        // that makes react-grid-layout collapse every widget into the
        // top-left corner (issue #1634).
        expect(gridTableCalls.length).toBeGreaterThan(0)
        gridTableCalls.forEach(call => {
            expect(call.items.length).toEqual(call.layout.length)
        })
        expect(gridTableCalls[0].items.length).toEqual(5)
    })
})
