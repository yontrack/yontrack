import {gql} from "graphql-request";
import React, {useContext, useEffect, useState} from "react";
import {Popover, Space, Table} from "antd";
import PromotionRun from "@components/promotionRuns/PromotionRun";
import {gqlDecorationFragment} from "@components/services/fragments";
import {FaBan} from "react-icons/fa";
import {DashboardWidgetCellContext} from "@components/dashboards/DashboardWidgetCellContextProvider";
import {useGraphQLClient} from "@components/providers/ConnectionContextProvider";
import BuildLink from "@components/builds/BuildLink";
import PromotionRuns from "@components/promotionRuns/PromotionRuns";
import BuildDeploymentChips from "@components/extension/environments/journey/BuildDeploymentChips";

const {Column} = Table;

export default function ProjectPromotionWidget({project, promotions, depth, label}) {

    const client = useGraphQLClient()
    const [runs, setRuns] = useState([])
    const [projects, setProjects] = useState([])

    useEffect(() => {
        if (client && project) {
            client.request(
                gql`
                    query GetProjectPromotions(
                        $project: String!,
                        $promotions: [String!]!,
                        $depth: Int = 0,
                        $label: String = null,
                    ) {
                        projects(name: $project) {
                            lastBuildsWithPromotions(promotions: $promotions) {
                                key: id
                                ...promotionRunContent
                                build {
                                    ...buildContent
                                    usingQualified(
                                        size: 10,
                                        depth: $depth,
                                        label: $label,
                                    ) {
                                        pageItems {
                                            qualifier
                                            build {
                                                ...buildContent
                                                branch {
                                                    project {
                                                        name
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    fragment buildContent on Build {
                        id
                        name
                        creation {
                            time
                            user
                        }
                        releaseProperty {
                            value
                        }
                        decorations {
                            ...decorationContent
                        }
                        promotionRuns(lastPerLevel: true) {
                            ...promotionRunContent
                        }
                    }

                    fragment promotionRunContent on PromotionRun {
                        id
                        creation {
                            time
                            user
                        }
                        description
                        annotatedDescription
                        promotionLevel {
                            id
                            name
                            description
                            annotatedDescription
                            image
                        }
                    }

                    ${gqlDecorationFragment}
                `,
                {project, promotions, depth, label}
            ).then(data => {
                const projectList = []
                data.projects[0].lastBuildsWithPromotions.forEach(run => {
                    run.build.usingQualified.pageItems.forEach(dependency => {
                        const projectName = dependency.build.branch.project.name
                        if (!run.dependencies) {
                            run.dependencies = {}
                        }
                        run.dependencies[projectName] = dependency
                        if (projectList.indexOf(projectName) < 0) {
                            projectList.push(projectName)
                        }
                    })
                })
                setProjects(projectList.sort())
                setRuns(data.projects[0].lastBuildsWithPromotions);
            })
        }
    }, [client, project, promotions, depth, label]);

    const {setTitle} = useContext(DashboardWidgetCellContext)
    useEffect(() => {
        setTitle(project ? `Promotions for ${project}` : "Project promotions")
    }, [project])

    return (
        <>
            <Table
                dataSource={runs}
                pagination={false}
                size="small"
            >
                <Column
                    title="Promotion"
                    render={(_, run) => <PromotionRun
                        promotionRun={run}
                        displayPromotionLevelName={true}
                    />}
                />
                <Column
                    title="Last build"
                    render={(_, run) => <Space>
                        <BuildLink
                            build={run.build}
                            displayTooltip={true}
                        />
                        <PromotionRuns promotionRuns={run.build.promotionRuns}/>
                        {/* Inert, like the build decorations and unlike the build search table:
                            a dashboard may hold several of these widgets, and a drawer per
                            widget means every one of them opening at once on `?slot=<id>`. The
                            chip states where the build is; the matrix widget beside it is where
                            a slot is operated. */}
                        <BuildDeploymentChips build={run.build}/>
                    </Space>}
                />
                {
                    projects.map(projectName => <Column
                        title={projectName}
                        key={projectName}
                        render={(_, run) => {
                            const dependency = run.dependencies && run.dependencies[projectName]
                            if (dependency) {
                                return <Space>
                                    <BuildLink
                                        build={dependency.build}
                                        displayTooltip={true}
                                    />
                                    <PromotionRuns promotionRuns={dependency.build.promotionRuns}/>
                                </Space>
                            } else {
                                return <Popover content={`No dependency on ${projectName}`}>
                                    <FaBan/>
                                </Popover>
                            }
                        }}
                    />)
                }
            </Table>
        </>
    )
}