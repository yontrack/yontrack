import {useContext} from "react";
import {gql} from "graphql-request";
import {gqlBuilds} from "@components/branches/branchQueries";
import BranchBuilds from "@components/branches/BranchBuilds";
import useRangeSelection from "@components/common/RangeSelection";
import {useQuery} from "@components/services/GraphQL";
import useBuildFilterSelection from "@components/branches/views/useBuildFilterSelection";
import useBranchBuildsPage from "@components/branches/views/useBranchBuildsPage";
import {AutoRefreshContext} from "@components/common/AutoRefresh";
import useLastKnown from "@components/common/useLastKnown";

// Stands in for the page info until the first page of builds has been loaded. `nextPage` is an empty
// object rather than absent, which is what the "load more" button treated as "nothing more to load"
// before this fetching moved here.
const emptyPageInfo = {nextPage: {}}

/**
 * Legacy branch content view: the table of the builds of the branch, with their promotions and their
 * validations.
 *
 * @param branch Branch being displayed
 */
export default function BuildsContentView({branch}) {

    // Selected build filter, shared with the other content views so switching view keeps it
    const {selectedBuildFilter, onSelectedBuildFilter, onPermalinkBuildFilter} =
        useBuildFilterSelection({branch})

    // The page of builds, on the same terms as every other content view
    const {builds, pageInfo, loadingBuilds, loadMore, reload} = useBranchBuildsPage({
        branch,
        query: gqlBuilds,
        selectedBuildFilter,
    })

    // Range selection, which forgets a build a refresh took off the list
    const rangeSelection = useRangeSelection({available: builds.map(build => build.id)})

    // Refetched on every tick of the auto refresh too, so that a stamp created since gets its column
    const {autoRefreshCount} = useContext(AutoRefreshContext)

    // Loading validation stamps
    const {data: loadedValidationStamps} = useQuery(
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
            deps: [branch, autoRefreshCount],
            initialData: [],
            dataFn: data => data.branches[0].validationStamps,
        }
    )
    // A refresh which fails does not take the columns away
    const validationStamps = useLastKnown(loadedValidationStamps)

    return (
        <BranchBuilds
            branch={branch}
            builds={builds}
            loadingBuilds={loadingBuilds}
            pageInfo={pageInfo ?? emptyPageInfo}
            onLoadMore={loadMore}
            rangeSelection={rangeSelection}
            validationStamps={validationStamps ?? []}
            onChange={reload}
            selectedBuildFilter={selectedBuildFilter}
            onSelectedBuildFilter={onSelectedBuildFilter}
            onPermalinkBuildFilter={onPermalinkBuildFilter}
        />
    )
}
