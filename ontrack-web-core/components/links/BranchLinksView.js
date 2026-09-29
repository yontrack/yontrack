import {useState} from "react";
import {CloseCommand} from "@components/common/Commands";
import {branchUri} from "@components/common/Links";
import Head from "next/head";
import {subBranchTitle} from "@components/common/Titles";
import MainPage from "@components/layouts/MainPage";
import {downToBranchBreadcrumbs} from "@components/common/Breadcrumbs";
import {gql} from "graphql-request";
import PageSection from "@components/common/PageSection";
import BranchLinksGraph from "@components/links/BranchLinksGraph";
import {useQuery} from "@components/services/GraphQL";
import {AutoRefreshButton, AutoRefreshContextProvider} from "@components/common/AutoRefresh";
import {FaTable} from "react-icons/fa";
import BranchLinksModeButton from "@components/links/BranchLinksModeButton";
import AutoVersioningLoadPRStatusesButton
    from "@components/extension/auto-versioning/AutoVersioningLoadPRStatusesButton";
import {Space} from "antd";

const NO_BRANCH = {project: {}}

export default function BranchLinksView({id}) {

    const [loadPullRequests, setLoadPullRequests] = useState(false)
    const [loadPullRequestsCount, setLoadPullRequestsCount] = useState(0)

    const loadPRStatuses = () => {
        setLoadPullRequests(true)
        setLoadPullRequestsCount(value => value + 1)
    }

    const {data: loadedBranch} = useQuery(
        gql`
            query GetBranch($id: Int!) {
                branches(id: $id) {
                    id
                    name
                    project {
                        id
                        name
                    }
                }
            }
        `,
        {
            variables: {id: Number(id)},
            deps: [id],
            condition: !!id,
            dataFn: data => data.branches[0],
        }
    )
    const branch = loadedBranch ?? NO_BRANCH

    const commands = loadedBranch ? [
        <CloseCommand key="close" href={branchUri(loadedBranch)}/>,
    ] : []

    return (
        <>
            <Head>
                {subBranchTitle(branch, "Links")}
            </Head>
            <MainPage
                title="Links"
                breadcrumbs={downToBranchBreadcrumbs({branch})}
                commands={commands}
            >
                <BranchLinksModeButton
                    icon={<FaTable/>}
                    mode="table"
                    title="Displays the dependencies as a table"
                    href={`/branch/${id}/links/table`}
                />
                <AutoRefreshContextProvider>
                    <PageSection
                        title={undefined}
                        extra={
                            <Space>
                                <AutoVersioningLoadPRStatusesButton onClick={loadPRStatuses}/>
                                <AutoRefreshButton/>
                            </Space>
                        }
                        padding={false}
                    >
                        <BranchLinksGraph branch={branch} loadPullRequests={loadPullRequests} loadPullRequestsCount={loadPullRequestsCount}/>
                    </PageSection>
                </AutoRefreshContextProvider>
            </MainPage>
        </>
    )
}