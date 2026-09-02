import {useEffect, useState} from "react";
import {useQuery} from "@components/services/GraphQL";
import {useEventForRefresh} from "@components/common/EventsContext";
import {useRefresh} from "@components/common/RefreshUtils";

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
 * reload on `build.created` - is the same, and lives here.
 *
 * @param branch Branch being displayed
 * @param query GraphQL document taking `$branchId`, `$offset`, `$size`, `$filterType`, `$filterData`
 *   and returning `branches[0].buildsPaginated`
 * @param selectedBuildFilter The active build filter, if any
 * @param size Page size
 */
export default function useBranchBuildsPage({branch, query, selectedBuildFilter, size = 10}) {

    const [pagination, setPagination] = useState({offset: 0, size})

    const buildCreated = useEventForRefresh("build.created")
    const [reloadCount, reload] = useRefresh()

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
            deps: [branch, pagination, selectedBuildFilter, reloadCount, buildCreated],
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
            setPagination(next)
        }
    }

    return {
        builds: page.builds,
        pageInfo: page.pageInfo,
        loadingBuilds,
        loadMore,
        reload,
    }
}
