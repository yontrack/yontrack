import Head from "next/head";
import {subBranchTitle} from "@components/common/Titles";
import {downToBranchBreadcrumbs} from "@components/common/Breadcrumbs";
import BranchLinksModeButton from "@components/links/BranchLinksModeButton";
import {FaArrowRight, FaCaretRight, FaProjectDiagram} from "react-icons/fa";
import MainPage from "@components/layouts/MainPage";
import {useEffect, useState} from "react";
import {CloseCommand} from "@components/common/Commands";
import {branchUri} from "@components/common/Links";
import LoadingContainer from "@components/common/LoadingContainer";
import {branchQuery} from "@components/links/BranchDependenciesFragments";
import {useBranch} from "@components/services/fragments";
import StandardTable from "@components/common/table/StandardTable";
import BuildLink from "@components/builds/BuildLink";
import {Divider, Form, Input, Space, Switch, Typography} from "antd";
import BranchLink from "@components/branches/BranchLink";
import ProjectLink from "@components/projects/ProjectLink";
import BuildPromotions from "@components/links/BuildPromotions";
import CheckStatus from "@components/common/CheckStatus";
import AutoVersioningInfo from "@components/extension/auto-versioning/AutoVersioningInfo";
import AutoVersioningLoadPRStatusesButton
    from "@components/extension/auto-versioning/AutoVersioningLoadPRStatusesButton";
import ChangelogButton from "@components/links/ChangelogButton";

export default function BranchLinksTableView({id}) {

    const {loading, branch} = useBranch(id)
    const [commands, setCommands] = useState([])

    useEffect(() => {
        if (branch.id) {
            setCommands([
                <CloseCommand key="close" href={branchUri(branch)}/>,
            ])
        }
    }, [branch])

    const flattenDeepDependencies = (data, filterFormData, links) => {
        const {consumer, dependency, errorsOnly} = filterFormData
        const branch = data.branch
        if (branch.downstreamLinks) {
            branch.downstreamLinks.forEach(downstreamLink => {
                const targetBranch = downstreamLink.branch
                const latestBuilds = targetBranch.latestBuilds
                let latestBuild = undefined
                if (latestBuilds && latestBuilds.length > 0) {
                    latestBuild = latestBuilds[0]
                }
                const latestOk = latestBuild && latestBuild.id === downstreamLink.targetBuild.id

                const keepLink =
                    (!consumer || downstreamLink.sourceBuild.branch.project.name.toLowerCase().indexOf(consumer.toLowerCase()) >= 0) &&
                    (!dependency || downstreamLink.targetBuild.branch.project.name.toLowerCase().indexOf(dependency.toLowerCase()) >= 0) &&
                    (!errorsOnly || !latestOk)

                if (keepLink) {
                    const link = {
                        latestBuild,
                        latestOk,
                        qualifier: downstreamLink.qualifier,
                        sourceBuild: downstreamLink.sourceBuild,
                        targetBuild: downstreamLink.targetBuild,
                        autoVersioning: downstreamLink.autoVersioning,
                    }
                    links.push(link)
                }

                flattenDeepDependencies(downstreamLink, filterFormData, links)
            })
        }
    }

    const flattenDependencies = (data, filterFormData) => {
        const links = []
        flattenDeepDependencies(data, filterFormData, links)
        return {
            pageInfo: {},
            pageItems: links,
        }
    }

    const [loadPullRequests, setLoadPullRequests] = useState(false)
    const [loadPullRequestsCount, setLoadPullRequestsCount] = useState(0)

    const loadPRStatuses = () => {
        setLoadPullRequests(true)
        setLoadPullRequestsCount(value => value + 1)
    }

    return (
        <>
            <Head>
                {subBranchTitle(branch, "Links")}
            </Head>
            <MainPage
                title="Links table"
                breadcrumbs={downToBranchBreadcrumbs({branch})}
                commands={commands}
            >
                <BranchLinksModeButton
                    icon={<FaProjectDiagram/>}
                    mode="graph"
                    title="Displays the dependencies as a graph"
                    href={`/branch/${id}/links`}
                />
                <LoadingContainer loading={loading}>
                    <StandardTable
                        id="branch-links"
                        filter={{}}
                        variables={{branchId: Number(id), loadPullRequests}}
                        query={branchQuery({downstream: true})}
                        queryNode={(data, filterFormData) => flattenDependencies(data, filterFormData)}
                        rowKey={link => `${link.targetBuild.branch.project.name}-${link.sourceBuild.branch.project.name}`}
                        reloadCount={loadPullRequestsCount}
                        filterExtraButtons={[
                            <AutoVersioningLoadPRStatusesButton key="load-pr-statuses" onClick={loadPRStatuses}/>,
                        ]}
                        columns={[
                            {
                                key: 'consumer',
                                title: 'Consumer',
                                render: (_, link) => <Space orientation="vertical">
                                    <Space size="small">
                                        <ProjectLink project={link.sourceBuild.branch.project}/>
                                        <Divider orientation="vertical"/>
                                        <BranchLink branch={link.sourceBuild.branch}/>
                                    </Space>
                                    <BuildLink build={link.sourceBuild}/>
                                    <BuildPromotions build={link.sourceBuild}/>
                                </Space>,
                            },
                            {
                                key: 'dependency',
                                title: 'Dependency',
                                render: (_, link) => <Space orientation="vertical">
                                    <Space size="small">
                                        <ProjectLink project={link.targetBuild.branch.project}/>
                                        {
                                            link.qualifier &&
                                            <Typography.Text>[{link.qualifier}]</Typography.Text>
                                        }
                                        <Divider orientation="vertical"/>
                                        <BranchLink branch={link.targetBuild.branch}/>
                                    </Space>
                                    <Space size="small">
                                        <FaArrowRight/>
                                        <BuildLink build={link.targetBuild}/>
                                    </Space>
                                    <BuildPromotions build={link.targetBuild}/>
                                </Space>,
                            },
                            {
                                key: 'latestOk',
                                title: 'Latest OK',
                                render: (_, link) => <CheckStatus
                                    value={link.latestOk}
                                    text="Using latest"
                                    noText="Not using latest"
                                />
                            },
                            {
                                key: 'latestBuild',
                                title: 'Latest build',
                                render: (_, link) => <>
                                    {
                                        link.latestBuild && !link.latestOk &&
                                        <Space orientation="vertical">
                                            <Space size="small">
                                                <FaCaretRight/>
                                                <BuildLink build={link.latestBuild}/>
                                            </Space>
                                            <Space size="small">
                                                <BuildPromotions build={link.latestBuild}/>
                                                <ChangelogButton
                                                    targetBuild={link.targetBuild}
                                                    lastTargetBuild={link.latestBuild}
                                                />
                                            </Space>
                                        </Space>
                                    }
                                </>,
                            },
                            {
                                key: 'autoVersioning',
                                title: 'Auto versioning',
                                render: (_, link) => link.autoVersioning && <AutoVersioningInfo
                                    autoVersioning={link.autoVersioning}
                                    branchLink={link}
                                />,
                            }
                        ]}
                        filterForm={[
                            <Form.Item
                                key="consumer"
                                name="consumer"
                            >
                                <Input placeholder="Consumer"/>
                            </Form.Item>,
                            <Form.Item
                                key="dependency"
                                name="dependency"
                            >
                                <Input placeholder="Dependency"/>
                            </Form.Item>,
                            <Form.Item
                                key="errorsOnly"
                                name="errorsOnly"
                                label="Errors only"
                            >
                                <Switch/>
                            </Form.Item>,
                        ]}
                    />
                </LoadingContainer>
            </MainPage>
        </>
    )
}