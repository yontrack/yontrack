import {useContext, useEffect} from "react";
import {gql} from "graphql-request";
import {Typography} from "antd";
import {useQuery} from "@components/services/GraphQL";
import {DashboardWidgetCellContext} from "@components/dashboards/DashboardWidgetCellContextProvider";
import LoadingContainer from "@components/common/LoadingContainer";
import PaddedContent from "@components/common/PaddedContent";
import ProjectLink from "@components/projects/ProjectLink";
import FindingsWidgetPanel from "@components/extension/findings/FindingsWidgetPanel";
import {hasReportedFindings} from "@components/extension/findings/findingsModel";

const gqlProjectFindingsWidget = gql`
    query ProjectFindingsWidget($project: String!) {
        projects(name: $project) {
            id
            name
            findingsSummary {
                open {
                    severity
                    count
                }
                openCount
                acceptedCount
                resolvedCount
                branches {
                    branch {
                        id
                        name
                    }
                    open {
                        severity
                        count
                    }
                    openCount
                    counting
                }
            }
        }
    }
`

/**
 * Open findings of a project by severity, with its accepted and resolved ones, and optionally the
 * branches having open findings among those which count for the project.
 */
export default function ProjectFindingsWidget({project, showBranches}) {

    const {data: loadedProject, loading, finished, error} = useQuery(
        gqlProjectFindingsWidget,
        {
            variables: {project},
            deps: [project],
            condition: !!project,
            dataFn: data => data.projects?.[0] ?? null,
        }
    )

    const {setTitle} = useContext(DashboardWidgetCellContext)
    useEffect(() => {
        if (loadedProject) {
            setTitle(<>Findings — <ProjectLink project={loadedProject}/></>)
        } else {
            setTitle("Project findings")
        }
    }, [loadedProject])

    const summary = loadedProject?.findingsSummary
    const branches = showBranches ?
        (summary?.branches ?? []).filter(it => it.counting && it.openCount > 0) :
        undefined

    return (
        <PaddedContent>
            {/* Never "not found" before the first fetch has answered */}
            <LoadingContainer loading={loading || (!!project && !finished)} error={error}>
                {
                    !project &&
                    <Typography.Text type="secondary">No project selected</Typography.Text>
                }
                {
                    project && !loadedProject &&
                    <Typography.Text type="secondary">Project {project} not found</Typography.Text>
                }
                {
                    loadedProject &&
                    <FindingsWidgetPanel
                        project={loadedProject}
                        summary={summary}
                        reported={hasReportedFindings(summary)}
                        branches={branches}
                        testIdPrefix="project-findings"
                    />
                }
            </LoadingContainer>
        </PaddedContent>
    )
}
