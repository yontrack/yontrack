import React from "react";
import {act, renderHook} from "@testing-library/react";

// A server holding the builds of one branch, most recent first, answering pages of them the way
// `buildsPaginated` does. A `useQuery` stand-in which answers synchronously from within its effect,
// on the same deps the real one would refetch on: what is under test is what the hook asks for and
// what it does with the answers, not how they travel.
let server

jest.mock("../../../../components/services/GraphQL", () => {
    const {useEffect, useState} = require("react")
    return {
        useQuery: (query, {variables, deps = [], initialData = null, dataFn = d => d}) => {
            const [data, setData] = useState(initialData)
            const [error, setError] = useState()
            const [finished, setFinished] = useState(false)
            useEffect(() => {
                server.requests.push(variables)
                if (server.failing) {
                    setError("Network error")
                    setData(null)
                } else {
                    setData(dataFn(server.answer(variables)))
                    setError(null)
                }
                setFinished(true)
            }, deps)
            return {data, error, finished, loading: false}
        },
    }
})

jest.mock("../../../../components/common/EventsContext", () => ({
    useEventForRefresh: () => 0,
}))

import {AutoRefreshContext} from "@components/common/AutoRefresh";
import useBranchBuildsPage from "@components/branches/views/useBranchBuildsPage";

const createServer = (count) => {
    let next = count
    const s = {
        // Most recent first: build-25, build-24, ... build-1
        builds: Array.from({length: count}, (_, i) => ({id: String(count - i), name: `build-${count - i}`})),
        failing: false,
        requests: [],
        createBuild: () => {
            next += 1
            s.builds = [{id: String(next), name: `build-${next}`}, ...s.builds]
        },
        answer: ({offset, size}) => {
            const pageItems = s.builds.slice(offset, offset + size)
            const nextOffset = offset + size
            return {
                branches: [{
                    buildsPaginated: {
                        pageInfo: {
                            totalSize: s.builds.length,
                            nextPage: nextOffset < s.builds.length ? {offset: nextOffset, size} : null,
                        },
                        pageItems,
                    },
                }],
            }
        },
    }
    return s
}

const branch = {id: "1", name: "main"}

const names = (builds) => builds.map(build => build.name)

const renderPage = ({autoRefreshCount = 0} = {}) => {
    let count = autoRefreshCount
    const wrapper = ({children}) => (
        <AutoRefreshContext.Provider value={{autoRefreshCount: count}}>
            {children}
        </AutoRefreshContext.Provider>
    )
    const hook = renderHook(() => useBranchBuildsPage({branch, query: "query", size: 10}), {wrapper})
    const tick = () => {
        count += 1
        hook.rerender()
    }
    return {...hook, tick}
}

describe('useBranchBuildsPage', () => {

    beforeEach(() => {
        server = createServer(25)
    })

    it('loads the first page', () => {
        const {result} = renderPage()
        expect(names(result.current.builds)).toEqual(names(server.builds.slice(0, 10)))
    })

    it('appends the next page on "load more"', () => {
        const {result} = renderPage()
        act(() => result.current.loadMore())
        expect(names(result.current.builds)).toEqual(names(server.builds.slice(0, 20)))
    })

    it('reloads the first page unchanged', () => {
        const {result} = renderPage()
        act(() => result.current.reload())
        expect(names(result.current.builds)).toEqual(names(server.builds.slice(0, 10)))
    })

    it('does not duplicate builds when reloading after "load more"', () => {
        const {result} = renderPage()
        act(() => result.current.loadMore())
        act(() => result.current.reload())
        const shown = names(result.current.builds)
        expect(shown).toEqual(names(server.builds.slice(0, 20)))
        expect(new Set(shown).size).toBe(shown.length)
    })

    it('reloads from the top, as many builds as are shown', () => {
        const {result} = renderPage()
        act(() => result.current.loadMore())
        act(() => result.current.reload())
        expect(server.requests[server.requests.length - 1]).toMatchObject({offset: 0, size: 20})
    })

    it('puts a new build at the top and drops the oldest one on a reload', () => {
        const {result} = renderPage()
        act(() => result.current.loadMore())
        const before = names(result.current.builds)
        server.createBuild()
        act(() => result.current.reload())
        const after = names(result.current.builds)
        expect(after).toHaveLength(20)
        expect(after[0]).toBe("build-26")
        expect(after.slice(1)).toEqual(before.slice(0, 19))
    })

    it('loads more by the page size after a reload, from where the list ends', () => {
        const {result} = renderPage()
        act(() => result.current.loadMore())
        act(() => result.current.reload())
        act(() => result.current.loadMore())
        expect(server.requests[server.requests.length - 1]).toMatchObject({offset: 20, size: 10})
        expect(names(result.current.builds)).toEqual(names(server.builds))
    })

    it('reloads on a tick of the auto refresh', () => {
        const {result, tick} = renderPage()
        server.createBuild()
        act(() => tick())
        expect(result.current.builds[0].name).toBe("build-26")
        expect(result.current.builds).toHaveLength(10)
    })

    it('does not reload on mount when the auto refresh has already ticked', () => {
        // Switching views mounts the new one under a context which has already counted ticks
        renderPage({autoRefreshCount: 3})
        expect(server.requests).toHaveLength(1)
    })

    it('keeps the builds already shown when a reload fails', () => {
        const {result, tick} = renderPage()
        act(() => result.current.loadMore())
        const before = names(result.current.builds)
        server.failing = true
        act(() => tick())
        expect(names(result.current.builds)).toEqual(before)
    })

})
