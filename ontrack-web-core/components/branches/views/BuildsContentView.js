import {gql} from "graphql-request";
import {gqlBuilds} from "@components/branches/branchQueries";
import BranchBuilds from "@components/branches/BranchBuilds";
import useRangeSelection from "@components/common/RangeSelection";
import {useQuery} from "@components/services/GraphQL";
import useBuildFilterSelection from "@components/branches/views/useBuildFilterSelection";
import useBranchBuildsPage from "@components/branches/views/useBranchBuildsPage";

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
