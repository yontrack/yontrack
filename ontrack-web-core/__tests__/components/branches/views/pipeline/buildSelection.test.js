import {
    buildSelectionParam,
    needsMoreBuilds,
    resolveSelectedBuildId,
} from "@components/branches/views/pipeline/buildSelection"

/**
 * The selection rules of the pipeline view, isolated from React and from the router.
 *
 * Build ids arrive as strings from the GraphQL `ID!` scalar and as strings from the query string,
 * but a caller holding a build object has a number in hand often enough that the comparison has to
 * be indifferent to which one it is given.
 */
describe('resolveSelectedBuildId', () => {

    const builds = [{id: "3"}, {id: "2"}, {id: "1"}]

    it('names the query parameter', () => {
        expect(buildSelectionParam).toBe('build')
    })

    it('selects the most recent build when nothing is requested', () => {
        expect(resolveSelectedBuildId(builds, undefined)).toBe("3")
    })

    it('honours a requested build which is loaded', () => {
        expect(resolveSelectedBuildId(builds, "2")).toBe("2")
    })

    it('accepts a requested build given as a number', () => {
        expect(resolveSelectedBuildId(builds, 2)).toBe("2")
    })

    it('falls back to the most recent build when the requested one is not loaded', () => {
        // A filter change dropping the selected build out of the page: a selection which is not
        // visible is never held on to
        expect(resolveSelectedBuildId(builds, "99")).toBe("3")
    })

    it('selects nothing when there are no builds at all', () => {
        expect(resolveSelectedBuildId([], "2")).toBeNull()
        expect(resolveSelectedBuildId(undefined, "2")).toBeNull()
    })

})

/**
 * A stage card names the latest build at its level, which can sit well below the loaded page. Asking
 * for it loads up to it rather than doing nothing.
 */
describe('needsMoreBuilds', () => {

    const builds = [{id: "3"}, {id: "2"}]
    const morePages = {nextPage: {offset: 2, size: 10}}
    const lastPage = {nextPage: null}

    it('is false when nothing is pending', () => {
        expect(needsMoreBuilds(builds, null, morePages)).toBe(false)
    })

    it('is false when the pending build is already loaded', () => {
        expect(needsMoreBuilds(builds, "2", morePages)).toBe(false)
    })

    it('is true when the pending build is beyond the loaded page and there is more to load', () => {
        expect(needsMoreBuilds(builds, "1", morePages)).toBe(true)
    })

    it('is false when there is no further page, so the wait ends rather than looping', () => {
        // The build may have been deleted, or filtered out: either way there is nothing more to try
        expect(needsMoreBuilds(builds, "1", lastPage)).toBe(false)
        expect(needsMoreBuilds(builds, "1", undefined)).toBe(false)
    })

})
