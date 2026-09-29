import {useRouter} from "next/router";
import {useGraphQLClient} from "@components/providers/ConnectionContextProvider";
import {useEffect, useState} from "react";
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

    const client = useGraphQLClient()

    const [project, setProject] = useState()
    const [breadcrumbs, setBreadcrumbs] = useState([])
    const [commands, setCommands] = useState([])
    useEffect(() => {
        if (client && id) {
            client.request(
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
                {id: Number(id)}
            ).then(data => {
                const project = data.projects[0];
                setProject(project)
                setBreadcrumbs(
                    downToProjectBreadcrumbs({project})
                )
                setCommands([
                    <CloseCommand key="close" href={projectUri(project)}/>,
                ])
            })
        }
    }, [client, id]);

    return (
        <>
            <StandardPage
                pageTitle="Auto-versioning audit as source project"
                breadcrumbs={breadcrumbs}
                commands={commands}
            >
                <Skeleton active loading={!project}>
                    <AutoVersioningAuditContextProvider sourceProject={project}>
                        <AutoVersioningAuditView/>
                    </AutoVersioningAuditContextProvider>
                </Skeleton>
            </StandardPage>
        </>
    )
}