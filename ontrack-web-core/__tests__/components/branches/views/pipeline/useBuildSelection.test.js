import "@testing-library/jest-dom"
import {useRef} from "react"
import {fireEvent, render, screen} from "@testing-library/react"
import useBuildSelection from "@components/branches/views/pipeline/useBuildSelection"

const mockRouter = {
    pathname: "/branch/[id]",
    query: {},
    replace: jest.fn(),
}

jest.mock("next/router", () => ({
    // Next hands out a fresh router object on every render, carrying a *copy* of the query
    useRouter: () => ({
        pathname: mockRouter.pathname,
        query: {...mockRouter.query},
        replace: mockRouter.replace,
    }),
}))

function Probe({builds, resolving = false}) {
    const {selectedBuildId, selectedBuild, selectBuild} = useBuildSelection({builds, resolving})
    // Stands in for a timeline card, which captures the callback once and keeps it
    const captured = useRef(selectBuild)
    return (
        <>
            <span data-testid="selected">{selectedBuildId ?? 'none'}</span>
            <span data-testid="selected-name">{selectedBuild?.name ?? 'none'}</span>
            <button onClick={() => captured.current("2")}>Pick 2</button>
        </>
    )
}

const builds = [
    {id: "3", name: "three"},
    {id: "2", name: "two"},
    {id: "1", name: "one"},
]

const renderProbe = ({query = {}, initialBuilds = builds, resolving = false} = {}) => {
    mockRouter.query = {id: "42", ...query}
    const {rerender} = render(<Probe builds={initialBuilds} resolving={resolving}/>)
    return {
        rerender: (nextBuilds, nextResolving = resolving) =>
            rerender(<Probe builds={nextBuilds} resolving={nextResolving}/>),
    }
}

const selected = () => screen.getByTestId('selected').textContent

describe('useBuildSelection', () => {

    beforeEach(() => {
        mockRouter.replace.mockReset()
    })

    describe('resolving the selection', () => {

        it('selects the most recent build when there is no ?build=', () => {
            renderProbe()
            expect(selected()).toBe("3")
            expect(screen.getByTestId('selected-name')).toHaveTextContent("three")
        })

        it('does not write the default selection back to the URL', () => {
            // A page load is not a choice; only an actual selection - or a correction - is worth an
            // entry in the URL
            renderProbe()
            expect(mockRouter.replace).not.toHaveBeenCalled()
        })

        it('honours ?build= as a deep link', () => {
            renderProbe({query: {build: "2"}})
            expect(selected()).toBe("2")
            expect(mockRouter.replace).not.toHaveBeenCalled()
        })

        it('selects nothing while no build is loaded yet', () => {
            renderProbe({initialBuilds: []})
            expect(selected()).toBe('none')
            // ... and does not blank out a ?build= which the next page may well contain
            expect(mockRouter.replace).not.toHaveBeenCalled()
        })

    })

    describe('selecting a build', () => {

        it('writes the choice back to the URL, shallowly, so it stays linkable', () => {
            renderProbe()
            fireEvent.click(screen.getByText("Pick 2"))
            expect(mockRouter.replace).toHaveBeenCalledWith(
                {
                    pathname: "/branch/[id]",
                    query: {id: "42", build: "2"},
                },
                undefined,
                {shallow: true},
            )
        })

        it('keeps the other query parameters, the content view among them', () => {
            renderProbe({query: {view: 'pipeline'}})
            fireEvent.click(screen.getByText("Pick 2"))
            expect(mockRouter.replace.mock.calls[0][0].query).toEqual({
                id: "42",
                view: 'pipeline',
                build: "2",
            })
        })

        it('writes back the query as it is at the moment of the click, not at the last render', () => {
            const {rerender} = renderProbe()
            mockRouter.query = {id: "42", view: 'pipeline'}
            rerender(builds)
            fireEvent.click(screen.getByText("Pick 2"))
            expect(mockRouter.replace.mock.calls[0][0].query).toEqual({
                id: "42",
                view: 'pipeline',
                build: "2",
            })
        })

    })

    describe('when the selection drops out of the page', () => {

        it('falls back to the most recent build', () => {
            // Stands in for a filter change: the build named by the URL is no longer in the page
            const {rerender} = renderProbe({query: {build: "2"}})
            expect(selected()).toBe("2")
            rerender([{id: "9", name: "nine"}])
            expect(selected()).toBe("9")
        })

        it('corrects the URL, so ?build= never names something invisible', () => {
            const {rerender} = renderProbe({query: {build: "2"}})
            mockRouter.query = {id: "42", build: "2"}
            rerender([{id: "9", name: "nine"}])
            expect(mockRouter.replace).toHaveBeenCalledWith(
                {
                    pathname: "/branch/[id]",
                    query: {id: "42", build: "9"},
                },
                undefined,
                {shallow: true},
            )
        })

    })

    describe('while the view is loading up to a requested build', () => {

        it('holds the request instead of falling back', () => {
            // A `?build=` naming an older build, or a stage card naming the latest build at its
            // level: the build is not in the page YET, which is a different thing from having
            // dropped out of it
            renderProbe({query: {build: "99"}, resolving: true})
            expect(selected()).toBe("3")
            // ... and above all does not erase the request the pages are being loaded for
            expect(mockRouter.replace).not.toHaveBeenCalled()
        })

        it('selects the build once it arrives', () => {
            const {rerender} = renderProbe({query: {build: "1"}, resolving: true})
            rerender([...builds, {id: "1", name: "one"}], false)
            expect(selected()).toBe("1")
            expect(mockRouter.replace).not.toHaveBeenCalled()
        })

        it('corrects the URL once the loading gives up on a build which is not there', () => {
            // No further page to load: the build was deleted, or the filter excludes it
            const {rerender} = renderProbe({query: {build: "99"}, resolving: true})
            rerender(builds, false)
            expect(selected()).toBe("3")
            expect(mockRouter.replace).toHaveBeenCalledWith(
                {
                    pathname: "/branch/[id]",
                    query: {id: "42", build: "3"},
                },
                undefined,
                {shallow: true},
            )
        })

    })

})
