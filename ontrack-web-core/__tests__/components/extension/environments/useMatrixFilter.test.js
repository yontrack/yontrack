import {act, renderHook} from "@testing-library/react"
import {useMatrixFilter} from "@components/extension/environments/matrix/useMatrixFilter"

const mockRouter = {
    isReady: true,
    pathname: "/extension/environments/environments",
    query: {},
    push: jest.fn(),
    replace: jest.fn(),
}

jest.mock("next/router", () => ({
    // A navigation is asynchronous: `push` returns and the query stays what it was until the router
    // has finished. Nothing here moves the query on its own, which is what makes that window testable.
    useRouter: () => ({...mockRouter, query: {...mockRouter.query}}),
}))

const lastQuery = (navigate) => navigate.mock.calls.at(-1)[0].query

describe("useMatrixFilter", () => {

    beforeEach(() => {
        mockRouter.query = {}
        mockRouter.push.mockClear()
        mockRouter.replace.mockClear()
        window.localStorage.clear()
    })

    it("keeps a change the address has not caught up with yet", () => {
        const {result} = renderHook(() => useMatrixFilter())

        act(() => result.current.apply({scope: "all"}))
        act(() => result.current.apply({project: "web"}))

        expect(lastQuery(mockRouter.push)).toEqual({scope: "all", project: "web"})
    })

    it("still keeps it when the screen renders again in the meantime", () => {
        const {result, rerender} = renderHook(() => useMatrixFilter())

        act(() => result.current.apply({scope: "all"}))
        rerender()
        act(() => result.current.apply({project: "web"}))

        expect(lastQuery(mockRouter.push)).toEqual({scope: "all", project: "web"})
    })

    it("does not let a correction of the screen undo what the reader has just chosen", () => {
        const {result} = renderHook(() => useMatrixFilter())

        act(() => result.current.apply({project: "web"}))
        act(() => result.current.apply({scope: "all"}, {replace: true}))

        expect(lastQuery(mockRouter.replace)).toEqual({scope: "all", project: "web"})
    })

    it("goes by the address again once it has moved", () => {
        const {result, rerender} = renderHook(() => useMatrixFilter())

        act(() => result.current.apply({scope: "all"}))
        // The reader goes somewhere else altogether - a Back, a link - before anything else happens
        mockRouter.query = {project: "api"}
        rerender()
        expect(result.current.filter.project).toBe("api")
        expect(result.current.filter.scope).toBe("favourites")

        act(() => result.current.apply({activity: true}))

        expect(lastQuery(mockRouter.push)).toEqual({project: "api", activity: "true"})
    })
})
