import {gql} from "graphql-request";
import React, {useContext, useEffect} from "react";
import {DashboardWidgetCellContext} from "@components/dashboards/DashboardWidgetCellContextProvider";
import {useQueries} from "@components/services/GraphQL";
import PaddedContent from "@components/common/PaddedContent";
import SimpleProjectList from "@components/projects/SimpleProjectList";
import {Skeleton} from "antd";
import {gqlDecorationFragment} from "@components/services/fragments";
import {gqlLabelFragment} from "@components/labels/LabelGraphQLFragments";

const gqlProjectByName = gql`
    query GetProjectByName($name: String!) {
        projects(name: $name) {
            id
            name
            favourite
            labels {
                ...labelFragment
            }
            decorations {
                ...decorationContent
            }
        }
    }

    ${gqlDecorationFragment}
    ${gqlLabelFragment}
`

export default function ProjectListWidget({projectNames}) {

    const {setTitle} = useContext(DashboardWidgetCellContext)
    useEffect(() => { setTitle("Project list") }, [])

    const hasProjects = !!projectNames && projectNames.length > 0
    const {data: results, loading, finished} = useQueries(
        (projectNames ?? []).map(name => ({
            query: gqlProjectByName,
            variables: {name},
        })),
        {
            deps: [projectNames],
            condition: hasProjects,
        }
    )
    const projects = hasProjects ?
        results.map(data => data.projects.length > 0 ? data.projects[0] : null).filter(it => it !== null) :
        []

    return (
        <PaddedContent>
            <Skeleton loading={hasProjects && (loading || !finished)} active>
                <SimpleProjectList
                    projects={projects}
                    emptyText={
                        <>
                            No project has been selected.
                        </>
                    }
                />
            </Skeleton>
        </PaddedContent>
    )
}