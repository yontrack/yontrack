import {useRouter} from "next/router";
import {useQuery} from "@components/services/GraphQL";
import {gql} from "graphql-request";
import StandardPage from "@components/layouts/StandardPage";
import AutoVersioningAuditView from "@components/extension/auto-versioning/AutoVersioningAuditView";
import {downToProjectBreadcrumbs} from "@components/common/Breadcrumbs";
import {CloseCommand} from "@components/common/Commands";
import {projectUri} from "@components/common/Links";
import AutoVersioningAuditContextProvider from "@components/extension/auto-versioning/AutoVersioningAuditContext";
import {Skeleton} from "antd";

export default function AutoVersioningAuditProjectTargetPage() {
    const router = useRouter()
    const {id} = router.query

    const {data: project} = useQuery(
        gql`
            query GetProject(
                $id: Int!,
            ) {
                projects(id: $id) {
                    id
                    name
                }
            }
        `,
        {
            variables: {id: Number(id)},
            deps: [id],
            condition: !!id,
            dataFn: data => data.projects[0],
        }
    )
    const breadcrumbs = project ? downToProjectBreadcrumbs({project}) : []
    const commands = project ? [
        <CloseCommand key="close" href={projectUri(project)}/>,
    ] : []

    return (
        <>
            <StandardPage
                pageTitle="Auto-versioning audit as target project"
                breadcrumbs={breadcrumbs}
                commands={commands}
            >
                <Skeleton active loading={!project}>
                    <AutoVersioningAuditContextProvider targetProject={project}>
                        <AutoVersioningAuditView/>
                    </AutoVersioningAuditContextProvider>
                </Skeleton>
            </StandardPage>
        </>
    )
}