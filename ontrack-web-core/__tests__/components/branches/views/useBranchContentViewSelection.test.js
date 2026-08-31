import "@testing-library/jest-dom"
import {useRef} from "react"
import {fireEvent, render, screen} from "@testing-library/react"
import {PreferencesContext} from "@components/providers/PreferencesProvider"
import useBranchContentViewSelection from "@components/branches/views/useBranchContentViewSelection"

// The builds content view drags the whole builds table in; only the registry is under test here
jest.mock("../../../../components/branches/views/BuildsContentView", () => () => <div/>)

const mockRouter = {
    pathname: "/branch/[id]",
    query: {},
    replace: jest.fn(),
}

jest.mock("next/router", () => ({
    // Next hands out a fresh router object on every render, carrying a *copy* of the query. A callback
    // captured in an earlier render therefore sees the query as it was then, not as it is now — which
    // is exactly the staleness the hook has to defend against.
    useRouter: () => ({
        pathname: mockRouter.pathname,
        query: {...mockRouter.query},
        replace: mockRouter.replace,
    }),
}))

const views = [
    {key: 'builds', name: "Builds"},
    {key: 'pipeline', name: "Pipeline"},
]

function Probe() {
    const {selectedViewKey, selectBranchContentView} = useBranchContentViewSelection({views})
    // Stands in for the branch view, which captures this callback inside the effect building its
    // command bar. That effect does not re-run on every render, so it keeps the callback it captured.
    const captured = useRef(selectBranchContentView)
    const select = (viewKey) => captured.current(viewKey)
    return (
        <>
            <span data-testid="selected">{selectedViewKey}</span>
            <button onClick={() => select('pipeline')}>Pick pipeline</button>
            <button onClick={() => select('builds')}>Pick builds</button>
            <button onClick={() => select('no-such-view')}>Pick unknown</button>
        </>
    )
}

const renderProbe = ({query = {}, selectedBranchViewKey = null} = {}) => {
    mockRouter.query = {id: "42", ...query}
    const setPreferences = jest.fn()
    // A fresh element every time: React bails out of re-rendering an identical element reference
    const tree = () => (
        <PreferencesContext.Provider value={{selectedBranchViewKey, setPreferences, loaded: true}}>
            <Probe/>
        </PreferencesContext.Provider>
    )
    const {rerender} = render(tree())
    // Re-rendering stands in for the branch view re-rendering without rebuilding its command bar,
    // which is when a captured callback would go stale
    return {setPreferences, rerender: () => rerender(tree())}
}

const selected = () => screen.getByTestId('selected').textContent

describe('useBranchContentViewSelection', () => {

    beforeEach(() => {
        mockRouter.replace.mockReset()
    })

    describe('resolving the selected view', () => {

        it('defaults to the default view when nothing is stored and no parameter is given', () => {
            renderProbe()
            expect(selected()).toBe('builds')
        })

        it('uses the stored preference when no parameter is given', () => {
            renderProbe({selectedBranchViewKey: 'pipeline'})
            expect(selected()).toBe('pipeline')
        })

        it('lets the ?view= parameter override the stored preference', () => {
            renderProbe({query: {view: 'pipeline'}, selectedBranchViewKey: 'builds'})
            expect(selected()).toBe('pipeline')
        })

        it('falls back to the default view when the ?view= parameter names no known view', () => {
            renderProbe({query: {view: 'no-such-view'}})
            expect(selected()).toBe('builds')
        })

        it('falls back to the default view when the stored preference names no known view', () => {
            renderProbe({selectedBranchViewKey: 'no-such-view'})
            expect(selected()).toBe('builds')
        })

    })

    describe('selecting a view', () => {

        it('writes the choice back to the URL, shallowly, so it stays linkable', () => {
            renderProbe()
            fireEvent.click(screen.getByText("Pick pipeline"))
            expect(mockRouter.replace).toHaveBeenCalledWith(
                {
                    pathname: "/branch/[id]",
                    query: {id: "42", view: 'pipeline'},
                },
                undefined,
                {shallow: true},
            )
        })

        it('keeps the other query parameters', () => {
            renderProbe({query: {buildFilter: '{"type":"x"}'}})
            fireEvent.click(screen.getByText("Pick pipeline"))
            expect(mockRouter.replace.mock.calls[0][0].query).toEqual({
                id: "42",
                buildFilter: '{"type":"x"}',
                view: 'pipeline',
            })
        })

        it('stores the choice as a preference', () => {
            const {setPreferences} = renderProbe()
            fireEvent.click(screen.getByText("Pick pipeline"))
            expect(setPreferences).toHaveBeenCalledWith({selectedBranchViewKey: 'pipeline'})
        })

        it('does not store the choice again when it is already the stored one', () => {
            const {setPreferences} = renderProbe({selectedBranchViewKey: 'pipeline'})
            fireEvent.click(screen.getByText("Pick pipeline"))
            expect(setPreferences).not.toHaveBeenCalled()
            // ... but the URL is still made to reflect the selection
            expect(mockRouter.replace).toHaveBeenCalled()
        })

        it('writes back the query as it is at the moment of the click, not at the last render', () => {
            // The branch view captures this callback inside the effect which builds its command bar, so
            // a callback reading a render-time copy of the query would drop whatever was added since
            const {rerender} = renderProbe()
            mockRouter.query = {id: "42", buildFilter: '{"type":"x"}'}
            rerender()
            fireEvent.click(screen.getByText("Pick pipeline"))
            expect(mockRouter.replace.mock.calls[0][0].query).toEqual({
                id: "42",
                buildFilter: '{"type":"x"}',
                view: 'pipeline',
            })
        })

        it('ignores a key which names no known view', () => {
            const {setPreferences} = renderProbe()
            fireEvent.click(screen.getByText("Pick unknown"))
            expect(setPreferences).not.toHaveBeenCalled()
            expect(mockRouter.replace).not.toHaveBeenCalled()
        })

    })

})
