import {gql} from "graphql-request";
import Head from "next/head";
import {pageTitle} from "@components/common/Titles";
import MainPage from "@components/layouts/MainPage";
import {downToProjectBreadcrumbs} from "@components/common/Breadcrumbs";
import {CloseCommand} from "@components/common/Commands";
import {projectUri} from "@components/common/Links";
import LoadingContainer from "@components/common/LoadingContainer";
import {Empty} from "antd";
import SCMCommitInfo from "@components/extension/scm/SCMCommitInfo";
import {useQuery} from "@components/services/GraphQL";

export default function SCMCommitInfoView({projectName, commit}) {

    const {loading, data: project} = useQuery(
        gql`
            query ScmCommitInfo(
                $projectName: String!,
                $commit: String!,
            ) {
                projects(name: $projectName) {
                    id
                    name
                    scmCommitInfo(commitId: $commit) {
                        scmDecoratedCommit {
                            commit {
                                id
                                shortId
                                author
                                timestamp
                                message
                                link
                            }
                            # This page is where the change log links to for the whole story,
                            # so it asks for the message in full, body included.
                            annotatedMessage(maxLength: 0)
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
        `,
        {
            initialData: {
                id: 0,
                name: projectName,
            },
            variables: {
                projectName,
                commit,
            },
            dataFn: data => data.projects ? data.projects[0] : {id: 0, name: projectName},
            deps: [projectName, commit],
        }
    )

    return (
        <>
            <Head>
                {pageTitle(`${projectName} commit ${commit}`)}
            </Head>
            <MainPage
                title={`Commit ${commit}`}
                breadcrumbs={project ? downToProjectBreadcrumbs({project}) : []}
                commands={[
                    <CloseCommand key="close" href={project ? projectUri(project) : ""}/>,
                ]}
            >
                <LoadingContainer loading={loading}>
                    {
                        project && project.scmCommitInfo && <SCMCommitInfo scmCommitInfo={project.scmCommitInfo}/>
                    }
                    {
                        (!project || !project.scmCommitInfo) && <Empty/>
                    }
                </LoadingContainer>
            </MainPage>
        </>
    )
}
