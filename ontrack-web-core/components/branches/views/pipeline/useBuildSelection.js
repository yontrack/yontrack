import {useCallback, useEffect, useRef} from "react";
import {useRouter} from "next/router";
import {buildSelectionParam, findBuildById, resolveSelectedBuildId} from "@components/branches/views/pipeline/buildSelection";

/**
 * Selection of the build inspected by the pipeline view.
 *
 * The `?build=` parameter is live: a selection is written back to the URL, shallowly, so that any
 * build a user is looking at can be linked to. A page load is not a selection though, so the default
 * - the most recent build - is NOT written back; only an explicit choice, or a correction, is.
 *
 * A CORRECTION is what happens when the build named by the URL is not in the loaded page: after a
 * filter change, most obviously. The selection falls back to the most recent build and the URL is
 * made to say so, because a `?build=` naming something the timeline is not showing would be a
 * permalink to a view nobody can reproduce.
 *
 * @param builds Loaded builds, most recent first
 */
export default function useBuildSelection({builds}) {

    const router = useRouter()

    const requestedId = router.query?.[buildSelectionParam]
    const selectedBuildId = resolveSelectedBuildId(builds, requestedId)
    const selectedBuild = findBuildById(builds, selectedBuildId)

    // The timeline cards capture this callback; `useRouter` hands out a fresh copy of the query on
    // every render, so a captured callback would write back the query of the render it was captured
    // in - losing a `?view=` or a `?buildFilter=` set since. Same defence as the content view
    // selection makes, for the same reason.
    const latest = useRef(null)
    latest.current = {router}

    const writeBuildParam = useCallback((buildId) => {
        const {router} = latest.current
        router.replace(
            {
                pathname: router.pathname,
                query: {
                    ...router.query,
                    [buildSelectionParam]: String(buildId),
                },
            },
            undefined,
            {shallow: true},
        )
    }, [])

    const selectBuild = useCallback((buildId) => {
        if (buildId === undefined || buildId === null) return
        writeBuildParam(buildId)
    }, [writeBuildParam])

    // Corrects a `?build=` which no longer names a visible build. Guarded on there being a request
    // AND a resolution: while the first page is still loading there is nothing to correct against,
    // and blanking the parameter then would throw away the deep link the user just followed.
    const staleRequest = requestedId !== undefined && requestedId !== null &&
        selectedBuildId !== null && String(requestedId) !== selectedBuildId
    useEffect(() => {
        if (staleRequest) {
            writeBuildParam(selectedBuildId)
        }
    }, [staleRequest, selectedBuildId, writeBuildParam])

    return {
        selectedBuildId,
        selectedBuild,
        selectBuild,
    }
}
