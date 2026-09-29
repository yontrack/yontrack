import {useContext, useEffect, useState} from "react";
import {gql} from "graphql-request";
import BranchList from "@components/branches/BranchList";
import {useEventForRefresh} from "@components/common/EventsContext";
import {Empty} from "antd";
import {DashboardWidgetCellContext} from "@components/dashboards/DashboardWidgetCellContextProvider";
import {useGraphQLClient} from "@components/providers/ConnectionContextProvider";
import PaddedContent from "@components/common/PaddedContent";
import {gqlBranchContentFragment} from "@components/branches/BranchGraphQLFragments";

export default function FavouriteBranchesWidget({project}) {

    const client = useGraphQLClient()
    const favouriteRefreshCount = useEventForRefresh("branch.favourite")
    const [branches, setBranches] = useState([])

    const {setTitle} = useContext(DashboardWidgetCellContext)
    useEffect(() => {
        setTitle(project ? `Favourite branches for ${project}` : "Favourite branches")
    }, [project])

    useEffect(() => {
        if (client) {
            client.request(
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
                {project}
            ).then(data => {
                setBranches(data.branches)
            })
        }
    }, [client, project, favouriteRefreshCount]);

    return (
        <PaddedContent>
            <BranchList
                branches={branches}
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