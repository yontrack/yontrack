import {useQuery} from "@components/services/GraphQL";
import {gql} from "graphql-request";
import {Space, Spin, Tooltip} from "antd";
import Link from "next/link";
import {FaLevelUpAlt} from "react-icons/fa";

export default function AutoVersioningAuditProjectSourceLink({name}) {

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
                <Tooltip title={`Auto-versioning audit for project source ${name}`}>
                    <Link href={`/extension/auto-versioning/audit-project-source/${project.id}`}>
                        <FaLevelUpAlt/>
                    </Link>
                </Tooltip>
            }
        </>
    )
}