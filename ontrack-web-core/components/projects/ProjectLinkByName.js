import {Space, Spin} from "antd";
import {useQuery} from "@components/services/GraphQL";
import {gql} from "graphql-request";
import ProjectLink from "@components/projects/ProjectLink";

export default function ProjectLinkByName({name}) {

    const {data: project, loading, finished} = useQuery(
        gql`
            query ProjectByName($name: String!) {
                projects(name: $name) {
                    id
                    name
                }
            }
        `,
        {
            variables: {name},
            deps: [name],
            dataFn: data => data.projects[0],
        }
    )

    return (
        <>
            {
                !project &&
                <Space>
                    {
                        (loading || !finished) &&
                        <Spin size="small" title="Loading link to project"/>
                    }
                    {name}
                </Space>
            }
            {
                project &&
                <ProjectLink project={project}/>
            }
        </>
    )

}