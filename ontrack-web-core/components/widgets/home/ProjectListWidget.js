import {gql} from "graphql-request";
import React, {useContext, useEffect, useState} from "react";
import {DashboardWidgetCellContext} from "@components/dashboards/DashboardWidgetCellContextProvider";
import {useGraphQLClient} from "@components/providers/ConnectionContextProvider";
import PaddedContent from "@components/common/PaddedContent";
import SimpleProjectList from "@components/projects/SimpleProjectList";
import {Skeleton} from "antd";
import {gqlDecorationFragment} from "@components/services/fragments";

export default function ProjectListWidget({projectNames}) {

    const client = useGraphQLClient()
    const [loading, setLoading] = useState(true)
    const [projects, setProjects] = useState([])

    const {setTitle} = useContext(DashboardWidgetCellContext)
    useEffect(() => { setTitle("Project list") }, [])

    const fetchProject = async (name) => {
        const data = await client.request(
            gql`
                query GetProjectByName($name: String!) {
                    projects(name: $name) {
                        id
                        name
                        favourite
                        decorations {
                            ...decorationContent
                        }
                    }
                }

                ${gqlDecorationFragment}
            `,
            {name}
        )
        const projects = data.projects
        if (projects.length > 0) {
            return projects[0]
        } else {
            return null
        }
    }

    useEffect(() => {
        if (client) {

            const fetchProjects = async () => {
                setLoading(true)
                try {
                    const projectPromises = projectNames.map(name => fetchProject(name))
                    const projectsData = await Promise.all(projectPromises)
                    setProjects(projectsData.filter(it => it !== null))
                } finally {
                    setLoading(false)
                }
            }

            // noinspection JSIgnoredPromiseFromCall
            fetchProjects()
        }
    }, [client, projectNames]);

    return (
        <PaddedContent>
            <Skeleton loading={loading} active>
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