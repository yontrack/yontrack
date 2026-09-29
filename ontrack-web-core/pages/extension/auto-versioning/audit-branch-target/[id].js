import {useRouter} from "next/router";
import {useQuery} from "@components/services/GraphQL";
import {gql} from "graphql-request";
import {gqlBranchContentFragment} from "@components/branches/BranchGraphQLFragments";
import StandardPage from "@components/layouts/StandardPage";
import AutoVersioningAuditView from "@components/extension/auto-versioning/AutoVersioningAuditView";
import {downToBranchBreadcrumbs} from "@components/common/Breadcrumbs";
import {CloseCommand} from "@components/common/Commands";
import {branchUri} from "@components/common/Links";
import AutoVersioningAuditContextProvider from "@components/extension/auto-versioning/AutoVersioningAuditContext";
import {Skeleton} from "antd";

export default function AutoVersioningAuditBranchTargetPage() {
    const router = useRouter()
    const {id} = router.query

    const {data: branch} = useQuery(
        gql`
            query GetBranch(
                $id: Int!,
            ) {
                branch(id: $id) {
                    ...BranchContent
                }
            }

            ${gqlBranchContentFragment}
        `,
        {
            variables: {id: Number(id)},
            deps: [id],
            condition: !!id,
            dataFn: data => data.branch,
        }
    )
    const breadcrumbs = branch ? downToBranchBreadcrumbs({branch}) : []
    const commands = branch ? [
        <CloseCommand key="close" href={branchUri(branch)}/>,
    ] : []

    return (
        <>
            <StandardPage
                pageTitle="Auto-versioning audit as target branch"
                breadcrumbs={breadcrumbs}
                commands={commands}
            >
                <Skeleton active loading={!branch}>
                    <AutoVersioningAuditContextProvider targetBranch={branch}>
                        <AutoVersioningAuditView/>
                    </AutoVersioningAuditContextProvider>
                </Skeleton>
            </StandardPage>
        </>
    )
}