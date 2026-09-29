import {useContext, useEffect} from "react";
import {gql} from "graphql-request";
import BranchList from "@components/branches/BranchList";
import {useEventForRefresh} from "@components/common/EventsContext";
import {Empty} from "antd";
import {DashboardWidgetCellContext} from "@components/dashboards/DashboardWidgetCellContextProvider";
import {useQuery} from "@components/services/GraphQL";
import PaddedContent from "@components/common/PaddedContent";
import {gqlBranchContentFragment} from "@components/branches/BranchGraphQLFragments";

const NO_ITEMS = []

export default function FavouriteBranchesWidget({project}) {

    const favouriteRefreshCount = useEventForRefresh("branch.favourite")

    const {setTitle} = useContext(DashboardWidgetCellContext)
    useEffect(() => {
        setTitle(project ? `Favourite branches for ${project}` : "Favourite branches")
    }, [project])

    const {data: branches} = useQuery(
        gql`
            query FavouriteBranches($project: String) {
                branches(favourite: true, project: $project) {
                    ...BranchContent
                    favourite
                    latestBuild: builds(count: 1) {
                        id
                        name
                        displayName
                    }
                    promotionLevels {
                        id
                        name
                        image
                        promotionRunsPaginated(size: 1) {
                            pageItems {
                                build {
                                    id
                                    name
                                    displayName
                                }
                            }
                        }
                    }
                }
            }
            ${gqlBranchContentFragment}
        `,
        {
            variables: {project},
            deps: [project, favouriteRefreshCount],
            initialData: NO_ITEMS,
            dataFn: data => data.branches,
        }
    )

    return (
        <PaddedContent>
            <BranchList
                branches={branches ?? NO_ITEMS}
                showProject={!project}
            />
            {
                (!branches || branches.length === 0) && <Empty
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description="No branch has been marked as a favourite in any project."
                />
            }
        </PaddedContent>
    )
}