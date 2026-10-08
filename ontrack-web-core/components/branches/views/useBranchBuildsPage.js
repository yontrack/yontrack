import {useContext, useEffect, useRef, useState} from "react";
import {useQuery} from "@components/services/GraphQL";
import {useEventForRefresh} from "@components/common/EventsContext";
import {AutoRefreshContext} from "@components/common/AutoRefresh";

/**
 * The page of builds a branch content view shows, accumulated across "load more".
 *
 * Shared by the content views because "load more" has to MEAN the same thing in all of them: same
 * generic filter, same page size, same accumulation. A user who scrolls a branch in one view and
 * switches to another must not find a different set of builds there, and a promise like that kept
 * by convention across two copies of the same twenty lines does not stay kept.
 *
 * The query itself is the caller's, because the views draw different things and so ask for
 * different fields. Everything around it - the variables, the pagination, the accumulation, the
 * reload on `build.created` and on every tick of the branch's auto refresh - is the same, and lives
 * here.
 *
 * A RELOAD IS NOT A REFETCH OF THE LAST PAGE. Once "load more" has run, the request in hand names
 * the last page appended, and asking for it again appended it a second time. A reload asks instead
 * for everything shown, in one page from the top - as many builds as are on screen - and REPLACES
 * the list with it: new builds come in at the top, and the oldest drop off the bottom.
 *
 * @param branch Branch being displayed
 * @param query GraphQL document taking `$branchId`, `$offset`, `$size`, `$filterType`, `$filterData`
 *   and returning `branches[0].buildsPaginated`
 * @param selectedBuildFilter The active build filter, if any
 * @param size Page size
 */
export default function useBranchBuildsPage({branch, query, selectedBuildFilter, size = 10}) {

    // A new object for every request, even an identical one: its identity is what refetches
    const [pagination, setPagination] = useState({offset: 0, size})

    const {data: buildsPage, loading, finished} = useQuery(
        query,
        {
            variables: {
                branchId: Number(branch.id),
                offset: pagination.offset,
                size: pagination.size,
                filterType: selectedBuildFilter?.type,
                // GraphQL type for the filter data is expected to be a string
                filterData: selectedBuildFilter ? JSON.stringify(selectedBuildFilter.data) : undefined,
            },
            deps: [branch, pagination, selectedBuildFilter],
            initialData: null,
            dataFn: data => data.branches[0].buildsPaginated,
        }
    )

    // `useQuery` only reports the loading as started from within its effect, so a view reading this
    // as loaded before the first fetch resolved would render its empty state over a branch which
    // does have builds.
    const loadingBuilds = loading || !finished

    // Accumulation across the pages. Not derivable from the latest response: "load more" appends, so
    // this is state built up over several of them.
    //
    // The builds and their page info are ONE piece of state, updated together, and that matters
    // beyond tidiness. `buildsPage` arrives a render before this effect appends it, so a caller
    // reading the page info straight off the response would see the last page's "there is nothing
    // more" while still holding the builds of the page before it - and would conclude the build it
    // was scrolling towards does not exist, one render before it arrives.
    const [page, setPage] = useState({builds: [], pageInfo: undefined})
    // `pagination` is read but deliberately not a dependency: the effect must run once per RESPONSE,
    // not once per request. Adding it would append the same page again the moment the offset moved,
    // before the page it named had arrived.
    useEffect(() => {
        if (buildsPage) {
            setPage(previous => ({
                builds: pagination.offset > 0
                    ? [...previous.builds, ...buildsPage.pageItems]
                    : buildsPage.pageItems,
                pageInfo: buildsPage.pageInfo,
            }))
        }
    }, [buildsPage])

    const loadMore = () => {
        const next = page.pageInfo?.nextPage
        // Asking again for the page already being loaded would append it twice. That happens for one
        // render after every response - the page info still names the offset just fetched - and it
        // also covers a user double-clicking the button.
        if (next && next.offset !== pagination.offset) {
            // By the page size, whatever size the last request had: after a reload, that request was
            // for every build shown, and "load more" must not double the list in one go.
            setPagination({offset: next.offset, size})
        }
    }

    // Offset 0, so that the effect above replaces instead of appending. A reload which fails sets
    // no `buildsPage`, so the builds already shown stay.
    const reload = () => {
        setPagination({offset: 0, size: Math.max(size, page.builds.length)})
    }

    // What asks for a reload without the user doing anything: a build created on this page, and a
    // tick of the auto refresh. Both are counters, and only a CHANGE of them is a request - a view
    // mounted after a switch finds the auto refresh having ticked already, and fetching its first
    // page is all it needs to do.
    const buildCreated = useEventForRefresh("build.created")
    const {autoRefreshCount} = useContext(AutoRefreshContext)
    const signals = useRef({buildCreated, autoRefreshCount})
    useEffect(() => {
        const previous = signals.current
        if (previous.buildCreated !== buildCreated || previous.autoRefreshCount !== autoRefreshCount) {
            signals.current = {buildCreated, autoRefreshCount}
            reload()
        }
    }, [buildCreated, autoRefreshCount])

    return {
        builds: page.builds,
        pageInfo: page.pageInfo,
        loadingBuilds,
        loadMore,
        reload,
    }
}
