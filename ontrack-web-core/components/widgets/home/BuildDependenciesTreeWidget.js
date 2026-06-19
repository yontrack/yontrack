import {gql} from "graphql-request";
import {useContext, useEffect, useState} from "react";
import {Empty} from "antd";
import {useGraphQLClient} from "@components/providers/ConnectionContextProvider";
import {DashboardWidgetCellContext} from "@components/dashboards/DashboardWidgetCellContextProvider";
import BuildLinksTree from "@components/links/BuildLinksTree";

export default function BuildDependenciesTreeWidget({title, project, branch, promotionLevel}) {

    const client = useGraphQLClient()
    const [build, setBuild] = useState(undefined)
    const [loading, setLoading] = useState(true)

    const {setTitle} = useContext(DashboardWidgetCellContext)
    useEffect(() => {
        setTitle(title || "Build dependencies")
    }, [title])

    useEffect(() => {
        if (client && project && branch && promotionLevel) {
            setLoading(true)
            client.request(
                gql`
                    query BuildDependenciesTreeWidgetLatestBuild(
                        $project: String!,
                        $branch: String!,
                        $promotionLevel: String!,
                    ) {
                        branches(project: $project, token: $branch) {
                            promotionStatuses(names: [$promotionLevel]) {
                                build {
                                    id
                                    name
                                }
                            }
                        }
                    }
                `,
                {project, branch, promotionLevel}
            ).then(data => {
                const run = data?.branches?.[0]?.promotionStatuses?.[0]
                setBuild(run?.build ?? null)
            }).finally(() => {
                setLoading(false)
            })
        }
    }, [client, project, branch, promotionLevel])

    if (!project || !branch || !promotionLevel) {
        return <Empty description="No promotion level configured"/>
    }

    if (!loading && build === null) {
        return <Empty description={`No build promoted yet for ${promotionLevel}`}/>
    }

    return (
        <>
            {build && <BuildLinksTree build={build}/>}
        </>
    )
}
