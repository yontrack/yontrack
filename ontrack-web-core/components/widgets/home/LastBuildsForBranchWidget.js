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

const NO_BUILDS = []

/**
 * The last builds of a branch, as the branch page shows them: one row per build, its promotions,
 * and one column per validation stamp.
 *
 * @param title Overrides the default title
 * @param project Name of the project
 * @param branch Name of the branch
 * @param count Number of builds to display
 * @param promotions Names of the promotions to display - all of them if empty
 * @param validations Names of the validations to display - all of them if empty
 */
export default function LastBuildsForBranchWidget({
                                                      title,
                                                      project,
                                                      branch,
                                                      count = 10,
                                                      promotions = [],
                                                      validations = [],
                                                  }) {

    // Incremented when a user action on a validation needs the builds to be reloaded
    const [reloadCount, setReloadCount] = useState(0)

    const {data: loadedBranch, loading: loadingBranch, finished: finishedBranch} = useQuery(
        gql`
            query LastBuildsForBranchWidget($project: String!, $branch: String!) {
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

    const {data: loadedBuilds, loading: loadingBuilds} = useQuery(
        gqlBuilds,
        {
            variables: {
                branchId: Number(loadedBranch?.id),
                offset: 0,
                size: count,
            },
            deps: [loadedBranch?.id, count, reloadCount],
            condition: !!loadedBranch,
            dataFn: data => data.branches[0].buildsPaginated.pageItems,
        }
    )
    const builds = loadedBuilds ?? NO_BUILDS

    const validationStamps = (loadedBranch?.validationStamps ?? [])
        .filter(stamp => validations.length === 0 || validations.includes(stamp.name))

    const displayedPromotionRuns = (build) => build.promotionRuns
        .filter(run => promotions.length === 0 || promotions.includes(run.promotionLevel.name))

    const onChange = () => setReloadCount(value => value + 1)

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
        } else if (project && branch && !finishedBranch) {
            // Not "no branch found" before the branch has been looked up at all
            setTitle("Last builds of branch")
        } else {
            setTitle("Last builds of branch (no branch found)")
        }
    }, [title, project, branch, loadedBranch, finishedBranch])

    return (
        <>
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
                                    displayedPromotionRuns(build).map(promotionRun =>
                                        <PromotionRunStep
                                            key={promotionRun.id}
                                            run={promotionRun}
                                        />
                                    )
                                }
                            </Space>
                        }
                    />
                    {
                        validationStamps.map(validationStamp => (
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
                        ))
                    }
                </Table>
            </LoadingContainer>
        </>
    )
}
