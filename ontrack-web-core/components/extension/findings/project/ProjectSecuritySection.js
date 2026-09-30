import {gql} from "graphql-request";
import Link from "next/link";
import {Empty, Space, Typography} from "antd";
import PageSection from "@components/common/PageSection";
import {useQuery} from "@components/services/GraphQL";
import {projectFindingsUri} from "@components/common/Links";
import FindingsSummaryCounts from "@components/extension/findings/FindingsSummaryCounts";
import FindingsBranchesTable from "@components/extension/findings/FindingsBranchesTable";
import {hasReportedFindings} from "@components/extension/findings/findingsModel";
import {isAuthorized} from "@components/common/authorizations";

export const gqlProjectFindingsSummary = gql`
    query ProjectFindingsSummary($id: Int!) {
        project(id: $id) {
            findingsSummary {
                open {
                    severity
                    count
                }
                openCount
                acceptedCount
                resolvedCount
                scanners
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
                }
            }
        }
    }
`

/**
 * The Security section of the project page: the open findings of the project by severity, and
 * their exposure per branch, each count linking to the findings page filtered on it.
 *
 * Named for what it holds — the security findings of every kind — and not for CVEs. Hidden from a
 * user who is not granted the view of the findings of the project (`findings/view` among the
 * authorizations of the project).
 */
export default function ProjectSecuritySection({project}) {

    const allowed = isAuthorized(project, 'findings', 'view')

    const {data: summary, loading, finished, error} = useQuery(
        gqlProjectFindingsSummary,
        {
            variables: {id: Number(project.id)},
            deps: [project.id],
            condition: allowed && !!project.id,
            dataFn: data => data.project?.findingsSummary,
        }
    )

    const exposedBranches = (summary?.branches ?? []).filter(it => it.openCount > 0)
    const hasFindings = hasReportedFindings(summary)

    if (!allowed) return null

    return (
        <PageSection
            id="project-security"
            loading={loading || !finished}
            title="Security"
            extra={<Link href={projectFindingsUri(project)}>All findings</Link>}
            padding={true}
        >
            {
                error &&
                <Typography.Text type="danger">{error}</Typography.Text>
            }
            {
                !error && !hasFindings &&
                <Empty
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description="No security finding has been reported for this project"
                />
            }
            {
                hasFindings &&
                <Space orientation="vertical" size={16} style={{width: '100%'}}>
                    <FindingsSummaryCounts
                        project={project}
                        summary={summary}
                        testIdPrefix="security"
                        title="Open findings"
                    />
                    {
                        exposedBranches.length > 0 ?
                            <FindingsBranchesTable
                                project={project}
                                branches={exposedBranches}
                                testIdPrefix="security"
                            /> :
                            <Typography.Text type="secondary">No finding is open on any branch.</Typography.Text>
                    }
                </Space>
            }
        </PageSection>
    )
}
