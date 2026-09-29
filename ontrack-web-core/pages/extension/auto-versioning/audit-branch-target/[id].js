import {useRouter} from "next/router";
import {useGraphQLClient} from "@components/providers/ConnectionContextProvider";
import {useEffect, useState} from "react";
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

    const client = useGraphQLClient()

    const [branch, setBranch] = useState()
    const [breadcrumbs, setBreadcrumbs] = useState([])
    const [commands, setCommands] = useState([])
    useEffect(() => {
        if (client && id) {
            client.request(
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
                {id: Number(id)}
            ).then(data => {
                const branch = data.branch;
                setBranch(branch)
                setBreadcrumbs(
                    downToBranchBreadcrumbs({branch})
                )
                setCommands([
                    <CloseCommand key="close" href={branchUri(branch)}/>,
                ])
            })
        }
    }, [client, id]);

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