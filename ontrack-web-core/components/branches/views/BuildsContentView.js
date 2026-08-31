import {useEffect, useState} from "react";
import {gql} from "graphql-request";
import {useRouter} from "next/router";
import {gqlBuilds} from "@components/branches/branchQueries";
import BranchBuilds from "@components/branches/BranchBuilds";
import useRangeSelection from "@components/common/RangeSelection";
import {getLocallySelectedBuildFilter, setLocallySelectedBuildFilter,} from "@components/storage/local";
import {useEventForRefresh} from "@components/common/EventsContext";
import {useQuery} from "@components/services/GraphQL";
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

    // Router (used for permalinks)
    const router = useRouter()

    // Pagination status
    const [pagination, setPagination] = useState({
        offset: 0,
        size: 10,
    })

    // Initially selected build filter
    let initialBuildFilter = undefined
    const {buildFilter} = router.query
    if (buildFilter) {
        try {
            initialBuildFilter = JSON.parse(buildFilter)
            // Clears the permalink, leaving the other parameters (like the content view) alone
            const {buildFilter: _, ...query} = router.query
            router.replace({pathname: router.pathname, query}, undefined, {shallow: true})
        } catch (ignored) {
        }
    } else {
        initialBuildFilter = getLocallySelectedBuildFilter(branch.id)
    }

    // Selected build filter
    const [selectedBuildFilter, setSelectedBuildFilter] = useState(initialBuildFilter)
    const onSelectedBuildFilter = (resource) => {
        setLocallySelectedBuildFilter(branch.id, resource)
        setSelectedBuildFilter(resource)
    }
    const onPermalinkBuildFilter = (resource) => {
        if (resource) {
            router.replace({
                pathname: router.pathname,
                query: {
                    ...router.query,
                    buildFilter: JSON.stringify(resource),
                },
            }, undefined, {shallow: true})
        }
    }

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
