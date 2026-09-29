import {useContext, useEffect, useState} from "react";
import {Empty, Typography} from "antd";
import {gql} from "graphql-request";
import {gqlDecorationFragment} from "@components/services/fragments";
import ProjectList from "@components/projects/ProjectList";
import {useEventForRefresh} from "@components/common/EventsContext";
import {DashboardWidgetCellContext} from "@components/dashboards/DashboardWidgetCellContextProvider";
import {useGraphQLClient} from "@components/providers/ConnectionContextProvider";
import PaddedContent from "@components/common/PaddedContent";
import {gqlProjectContentFragment} from "@components/projects/ProjectGraphQLFragments";

export default function FavouriteProjectsWidget() {

    const client = useGraphQLClient()
    const refreshCount = useEventForRefresh("project.favourite")

    const [projects, setProjects] = useState([])
    useEffect(() => {
        if (client) {
            client.request(
                gql`
                    query FavouriteProjects {
                        projects(favourites: true) {
                            ...ProjectContent
                            favourite
                            decorations {
                                ...decorationContent
                            }
                            branches(useModel: true, count: 10) {
                                id
                                name
                                latestBuild: builds(count: 1) {
                                    id
                                    name
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
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    ${gqlDecorationFragment}
                    ${gqlProjectContentFragment}
                `
            ).then(data => {
                setProjects(data.projects)
            })
        }
    }, [client, refreshCount]);

    const {setTitle} = useContext(DashboardWidgetCellContext)
    useEffect(() => {
        setTitle("Favourite projects")
    }, [])

    return (
        <PaddedContent>
            <ProjectList projects={projects}/>
            {
                (!projects || projects.length === 0) && <Empty
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description={
                        <Typography.Text>
                            No project has been selected as a favourite yet.
                        </Typography.Text>
                    }
                />
            }
        </PaddedContent>
    )
}