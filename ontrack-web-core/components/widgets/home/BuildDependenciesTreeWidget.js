import {gql} from "graphql-request";
import {useContext, useEffect} from "react";
import {Empty} from "antd";
import {useQuery} from "@components/services/GraphQL";
import {DashboardWidgetCellContext} from "@components/dashboards/DashboardWidgetCellContextProvider";
import BuildLinksTree from "@components/links/BuildLinksTree";

export default function BuildDependenciesTreeWidget({title, project, branch, promotionLevel}) {

    const {setTitle} = useContext(DashboardWidgetCellContext)
    useEffect(() => {
        setTitle(title || "Build dependencies")
    }, [title])

    const {data: build, loading, finished, error} = useQuery(
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
        {
            variables: {project, branch, promotionLevel},
            deps: [project, branch, promotionLevel],
            condition: !!(project && branch && promotionLevel),
            dataFn: data => {
                const run = data?.branches?.[0]?.promotionStatuses?.[0]
                return run?.build ?? null
            },
        }
    )

    if (!project || !branch || !promotionLevel) {
        return <Empty description="No promotion level configured"/>
    }

    // A failed request is not a missing build
    if (finished && !loading && !error && build === null) {
        return <Empty description={`No build promoted yet for ${promotionLevel}`}/>
    }

    return (
        <>
            {build && <BuildLinksTree build={build}/>}
        </>
    )
}
