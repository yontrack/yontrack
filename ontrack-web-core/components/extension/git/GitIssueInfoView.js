import {useQuery} from "@components/services/GraphQL";
import {gql} from "graphql-request";
import Head from "next/head";
import {pageTitle} from "@components/common/Titles";
import MainPage from "@components/layouts/MainPage";
import {downToProjectBreadcrumbs} from "@components/common/Breadcrumbs";
import {CloseCommand} from "@components/common/Commands";
import {projectUri} from "@components/common/Links";
import LoadingContainer from "@components/common/LoadingContainer";
import {Empty} from "antd";
import {useEffect, useState} from "react";
import SCMIssueInfo from "@components/extension/scm/SCMIssueInfo";

export default function GitIssueInfoView({projectId, issueKey}) {

    const [issueDisplayKey, setIssueDisplayKey] = useState(issueKey)

    const initialData = {
        project: {
            id: projectId,
            name: ""
        }
    }

    const {loading, finished, data: queriedData} = useQuery(
        gql`
            query GitIssueInfo(
                $projectId: Int!,
                $issueKey: String!,
            ) {
                project(id: $projectId) {
                    id
                    name
                    scmIssueInfo(issueKey: $issueKey) {
                        issueServiceConfigurationRepresentation {
                            id
                            serviceId
                            name
                        }
                        issue {
                            displayKey
                            url
                            summary
                            rawIssue
                        }
                        scmCommitInfo {
                            scmDecoratedCommit {
                                commit {
                                    id
                                    shortId
                                    author
                                    timestamp
                                    message
                                    link
                                }
                                annotatedMessage
                            }
                            branchInfos {
                                type
                                branchInfoList {
                                    branch {
                                        id
                                        name
                                        displayName
                                        disabled
                                        project {
                                            id
                                            name
                                        }
                                    }
                                    firstBuild {
                                        id
                                        name
                                        displayName
                                        creation {
                                            time
                                        }
                                    }
                                    promotions {
                                        id
                                        promotionLevel {
                                            id
                                            name
                                            image
                                        }
                                        build {
                                            id
                                            name
                                            displayName
                                        }
                                        creation {
                                            time
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        `,
        {
            initialData,
            variables: {
                projectId: Number(projectId),
                issueKey,
            },
            deps: [projectId, issueKey],
        }
    )

    // A failed query leaves `null` behind, where the old hook kept the initial data
    const data = queriedData ?? initialData

    useEffect(() => {
        const displayKey = queriedData?.scmIssueInfo?.issue?.displayKey
        if (displayKey) {
            setIssueDisplayKey(displayKey)
        }
    }, [queriedData])

    return (
        <>
            <Head>
                {pageTitle(`${data.project.name} issue ${issueDisplayKey}`)}
            </Head>
            <MainPage
                title={`Issue ${issueDisplayKey}`}
                breadcrumbs={downToProjectBreadcrumbs(data)}
                commands={[
                    <CloseCommand key="close" href={projectUri(data.project)}/>,
                ]}
            >
                <LoadingContainer loading={loading || !finished}>
                    {
                        data.project.scmIssueInfo && <SCMIssueInfo scmIssueInfo={data.project.scmIssueInfo}/>
                    }
                    {
                        !data.project.scmIssueInfo && <Empty/>
                    }
                </LoadingContainer>
            </MainPage>
        </>
    )
}