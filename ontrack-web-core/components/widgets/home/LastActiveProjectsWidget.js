import {useContext, useEffect} from "react";
import {useEventForRefresh} from "@components/common/EventsContext";
import {gql} from "graphql-request";
import {gqlDecorationFragment} from "@components/services/fragments";
import {DashboardWidgetCellContext} from "@components/dashboards/DashboardWidgetCellContextProvider";
import PaddedContent from "@components/common/PaddedContent";
import SimpleProjectList from "@components/projects/SimpleProjectList";
import {gqlProjectContentFragment} from "@components/projects/ProjectGraphQLFragments";
import {useQuery} from "@components/services/GraphQL";

export default function LastActiveProjectsWidget({count}) {

    const projectsRefreshCount = useEventForRefresh("project.created")
    const favouritesRefresh = useEventForRefresh("project.favourite")

    const {setTitle} = useContext(DashboardWidgetCellContext)
    useEffect(() => { setTitle(`Last ${count} active projects`) }, [count])

    const {data: projects} = useQuery(
        gql`
            query LastActiveProjects($count: Int! = 10) {
                lastActiveProjects(count: $count) {
                    ...ProjectContent
                    favourite
                    decorations {
                        ...decorationContent
                    }
                }
            }

            ${gqlProjectContentFragment}
            ${gqlDecorationFragment}
        `,
        {
            variables: {count},
            initialData: [],
            deps: [count, projectsRefreshCount, favouritesRefresh],
            dataFn: data => data.lastActiveProjects,
        }
    )

    return (
        <PaddedContent>
            <SimpleProjectList
                projects={projects}
                emptyText={
                    <>
                        No project has been created in Ontrack yet.
                        You can start <a
                        href="https://static.nemerosa.net/ontrack/release/latest/docs/doc/index.html#feeding">feeding
                        information</a> in Ontrack
                        automatically from your CI engine, using its API or other means.
                    </>
                }
            />
        </PaddedContent>
    )
}