import {useContext, useEffect, useState} from "react";
import {gql} from "graphql-request";
import {Divider, Space} from "antd";
import Link from "next/link";
import {FaExternalLinkAlt} from "react-icons/fa";
import {DashboardWidgetCellContext} from "@components/dashboards/DashboardWidgetCellContextProvider";
import {useQuery} from "@components/services/GraphQL";
import {gqlBuilds} from "@components/branches/branchQueries";
import LoadingContainer from "@components/common/LoadingContainer";
import Table from "@components/common/table/Table";
import BuildBox from "@components/builds/BuildBox";
import PromotionRunStep from "@components/promotionRuns/PromotionRunStep";
import ValidationStampHeader from "@components/branches/ValidationStampHeader";
import ValidationRunCell from "@components/branches/ValidationRunCell";
import BranchLink from "@components/branches/BranchLink";
import ProjectLink from "@components/projects/ProjectLink";
import {branchUri} from "@components/common/Links";

const {Column} = Table;

// Stable references, so that an absent list does not change on every render
const NO_BUILDS = []
const NO_NAMES = []

/**
 * Keeps an item when no names are configured, or when its name is one of them.
 */
const acceptName = (names, name) => !names || names.length === 0 || names.includes(name)

/**
 * Last builds of a branch, shown like the builds grid of the branch page: one row per build, its
 * promotions, and one column per validation stamp.
 *
 * @param title Overriding title
 * @param project Name of the project
 * @param branch Name of the branch
 * @param count Number of builds to display
 * @param promotions Names of the promotion levels to display, all of them if empty
 * @param validations Names of the validation stamps to display as columns, all of them if empty
 */
export default function LastBuildsForBranchWidget({
                                                      title,
                                                      project,
                                                      branch,
                                                      count = 10,
                                                      promotions = NO_NAMES,
                                                      validations = NO_NAMES,
                                                  }) {

    // The branch and its validation stamps
    const {data: loadedBranch, loading: loadingBranch, finished: branchFinished} = useQuery(
        gql`
            query LastBuildsForBranchWidgetBranch(
                $project: String!,
                $branch: String!,
            ) {
                branch: branchByName(project: $project, name: $branch) {
                    id
                    name
                    displayName
                    project {
                        id
                        name
                    }
                    validationStamps {
                        id
                        name
                        description
                        annotatedDescription
                        image
                        dataType {
                            descriptor {
                                id
                                displayName
                            }
                            config
                        }
                    }
                }
            }
        `,
        {
            variables: {project, branch},
            deps: [project, branch],
            condition: !!(project && branch),
            dataFn: data => data.branch,
        }
    )

    // Bumped after a user action in a validation cell, to reload the builds
    const [reloadCount, setReloadCount] = useState(0)
    const onChange = () => setReloadCount(value => value + 1)

    // The last builds of the branch
    const branchId = loadedBranch?.id
    const {data: loadedBuilds, loading: loadingBuilds} = useQuery(
        gqlBuilds,
        {
            variables: {
                branchId: Number(branchId),
                offset: 0,
                size: count,
            },
            deps: [branchId, count, reloadCount],
            condition: !!branchId,
            dataFn: data => data.branches[0]?.buildsPaginated?.pageItems,
        }
    )
    const builds = loadedBuilds ?? NO_BUILDS

    // Columns and promotions, restricted to the configured names
    const validationStamps = (loadedBranch?.validationStamps ?? [])
        .filter(validationStamp => acceptName(validations, validationStamp.name))
    const promotionRunsOf = build => (build.promotionRuns ?? [])
        .filter(run => acceptName(promotions, run.promotionLevel.name))

    // Title of the widget
    const {setTitle} = useContext(DashboardWidgetCellContext)
    useEffect(() => {
        if (title) {
            if (loadedBranch) {
                setTitle(
                    <Space>
                        {title}
                        <Link href={branchUri(loadedBranch)}>
                            <FaExternalLinkAlt/>
                        </Link>
                    </Space>
                )
            } else {
                setTitle(title)
            }
        } else if (loadedBranch) {
            setTitle(
                <>
                    Last builds of <BranchLink branch={loadedBranch}/>
                    <Divider orientation="vertical"/>
                    <ProjectLink project={loadedBranch.project}/>
                </>
            )
        } else if (project && branch && !branchFinished) {
            setTitle("Last builds of branch")
        } else {
            setTitle("Last builds of branch (no branch found)")
        }
    }, [title, project, branch, loadedBranch, branchFinished])

    return (
        <LoadingContainer loading={loadingBranch || loadingBuilds}>
            <Table
                data-testid="last-builds-for-branch"
                size="small"
                dataSource={builds}
                pagination={false}
                scroll={{x: 'max-content'}}
            >
                <Column
                    key="build"
                    title="Build"
                    fixed="left"
                    render={(_, build) =>
                        <BuildBox build={build} displayDecorations={true}/>
                    }
                />
                <Column
                    key="promotions"
                    title="Promotions"
                    fixed="left"
                    render={(_, build) =>
                        <Space size={8}>
                            {
                                promotionRunsOf(build).map(run =>
                                    <PromotionRunStep key={run.id} run={run}/>
                                )
                            }
                        </Space>
                    }
                />
                {/* One column per validation stamp */}
                {
                    validationStamps.map(validationStamp =>
                        <Column
                            key={validationStamp.name}
                            className="ot-validation-stamp"
                            title={<ValidationStampHeader validationStamp={validationStamp}/>}
                            render={(_, build) =>
                                <ValidationRunCell
                                    build={build}
                                    validationStamp={validationStamp}
                                    onChange={onChange}
                                />
                            }
                        />
                    )
                }
            </Table>
        </LoadingContainer>
    )
}
