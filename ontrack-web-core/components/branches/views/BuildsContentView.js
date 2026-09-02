import {useEffect, useState} from "react";
import {gql} from "graphql-request";
import {gqlBuilds} from "@components/branches/branchQueries";
import BranchBuilds from "@components/branches/BranchBuilds";
import useRangeSelection from "@components/common/RangeSelection";
import {useEventForRefresh} from "@components/common/EventsContext";
import {useQuery} from "@components/services/GraphQL";
import useBuildFilterSelection from "@components/branches/views/useBuildFilterSelection";
import {useRefresh} from "@components/common/RefreshUtils";

// Stands in for the page info until the first page of builds has been loaded. `nextPage` is an empty
// object rather than absent, which is what the "load more" button treated as "nothing more to load"
// before this fetching moved here.
const emptyPageInfo = {nextPage: {}}

/**
 * Legacy branch content view: the table of the builds of the branch, with their promotions and their
 * validations.
 *
 * Each content view fetches its own data, so adding another view never means editing a shared fetcher.
 *
 * @param branch Branch being displayed
 */
export default function BuildsContentView({branch}) {

    // Pagination status
    const [pagination, setPagination] = useState({
        offset: 0,
        size: 10,
    })

    // Selected build filter, shared with the other content views so switching view keeps it
    const {selectedBuildFilter, onSelectedBuildFilter, onPermalinkBuildFilter} =
        useBuildFilterSelection({branch})

    // Build created
    const buildCreated = useEventForRefresh("build.created")

    // Forcing the reload of the builds
    const [buildsReloads, reloadBuilds] = useRefresh()

    // Loading the builds
    const {data: buildsPage, loading, finished} = useQuery(
        gqlBuilds,
        {
            variables: {
                branchId: Number(branch.id),
                offset: pagination.offset,
                size: pagination.size,
                filterType: selectedBuildFilter?.type,
                // GraphQL type for the filter data is expected to be a string
                filterData: selectedBuildFilter ? JSON.stringify(selectedBuildFilter.data) : undefined,
            },
            deps: [branch, pagination, buildsReloads, selectedBuildFilter, buildCreated],
            initialData: null,
            dataFn: data => data.branches[0].buildsPaginated,
        }
    )

    // `useQuery` only reports the loading as started from within its effect: until the first fetch is
    // over, the view must still show as loading, the way it did before the migration.
    const loadingBuilds = loading || !finished

    // Builds page info (for the more button)
    const buildsPageInfo = buildsPage?.pageInfo ?? emptyPageInfo

    // Accumulation of the builds across the pages
    const [builds, setBuilds] = useState([])
    useEffect(() => {
        if (buildsPage) {
            // Completing the builds list of a pagination request (based on offset > 0)
            if (pagination.offset > 0) {
                setBuilds(previous => [...previous, ...buildsPage.pageItems])
            } else {
                setBuilds(buildsPage.pageItems)
            }
        }
    }, [buildsPage])

    // Loading more builds
    const onLoadMoreBuilds = () => {
        if (buildsPageInfo.nextPage) {
            setPagination(buildsPageInfo.nextPage)
        }
    }

    // Range selection
    const rangeSelection = useRangeSelection()

    // Loading validation stamps
    const {data: validationStamps} = useQuery(
        gql`
            query GetValidationStamps($branchId: Int!) {
                branches(id: $branchId) {
                    validationStamps {
                        id
                        name
                        description
                        annotatedDescription
                        image
                        dataType {
                            descriptor {
                                id
                                displayName
                            }
                            config
                        }
                    }
                }
            }
        `,
        {
            variables: {branchId: Number(branch.id)},
            deps: [branch],
            initialData: [],
            dataFn: data => data.branches[0].validationStamps,
        }
    )

    return (
        <BranchBuilds
            branch={branch}
            builds={builds}
            loadingBuilds={loadingBuilds}
            pageInfo={buildsPageInfo}
            onLoadMore={onLoadMoreBuilds}
            rangeSelection={rangeSelection}
            validationStamps={validationStamps ?? []}
            onChange={reloadBuilds}
            selectedBuildFilter={selectedBuildFilter}
            onSelectedBuildFilter={onSelectedBuildFilter}
            onPermalinkBuildFilter={onPermalinkBuildFilter}
        />
    )
}
