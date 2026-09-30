import {useContext, useEffect} from "react";
import {gql} from "graphql-request";
import {Typography} from "antd";
import {useQuery} from "@components/services/GraphQL";
import {DashboardWidgetCellContext} from "@components/dashboards/DashboardWidgetCellContextProvider";
import LoadingContainer from "@components/common/LoadingContainer";
import PaddedContent from "@components/common/PaddedContent";
import BranchLink from "@components/branches/BranchLink";
import ProjectLink from "@components/projects/ProjectLink";
import FindingsWidgetPanel from "@components/extension/findings/FindingsWidgetPanel";

const gqlBranchFindingsWidget = gql`
    query BranchFindingsWidget($project: String!, $branch: String!) {
        branch: branchByName(project: $project, name: $branch) {
            id
            name
            disabled
            project {
                id
                name
            }
            findingsSummary {
                open {
                    severity
                    count
                }
                openCount
                acceptedCount
                resolvedCount
                hasExposures
            }
        }
    }
`

/**
 * Findings open on a branch by severity, with the ones accepted and resolved on it.
 *
 * A disabled branch does not count for the project, but its own findings are still shown, with a
 * hint that it is disabled.
 */
export default function BranchFindingsWidget({project, branch}) {

    const {data: loadedBranch, loading, finished, error} = useQuery(
        gqlBranchFindingsWidget,
        {
            variables: {project, branch},
            deps: [project, branch],
            condition: !!(project && branch),
            dataFn: data => data.branch ?? null,
        }
    )

    const {setTitle} = useContext(DashboardWidgetCellContext)
    useEffect(() => {
        if (loadedBranch) {
            setTitle(
                <>
                    Findings — <ProjectLink project={loadedBranch.project}/>/<BranchLink branch={loadedBranch}/>
                </>
            )
        } else {
            setTitle("Branch findings")
        }
    }, [loadedBranch])

    const summary = loadedBranch?.findingsSummary

    return (
        <PaddedContent>
            {/* Never "not found" before the first fetch has answered */}
            <LoadingContainer loading={loading || (!!(project && branch) && !finished)} error={error}>
                {
                    !(project && branch) &&
                    <Typography.Text type="secondary">No branch selected</Typography.Text>
                }
                {
                    project && branch && !loadedBranch &&
                    <Typography.Text type="secondary">Branch {project}/{branch} not found</Typography.Text>
                }
                {
                    loadedBranch &&
                    <FindingsWidgetPanel
                        project={loadedBranch.project}
                        summary={summary}
                        reported={summary?.hasExposures}
                        branch={loadedBranch.name}
                        hint={loadedBranch.disabled ? "This is a disabled branch: it does not count for the project." : undefined}
                        testIdPrefix="branch-findings"
                    />
                }
            </LoadingContainer>
        </PaddedContent>
    )
}
