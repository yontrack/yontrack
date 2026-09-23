import {gql} from "graphql-request";
import Link from "next/link";
import {Empty, Space, Table, Typography} from "antd";
import PageSection from "@components/common/PageSection";
import {useQuery} from "@components/services/GraphQL";
import {projectFindingsUri} from "@components/common/Links";
import FindingSeverityTag from "@components/extension/findings/FindingSeverityTag";
import {FINDING_SEVERITIES, severityName} from "@components/extension/findings/findingsModel";
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

const countOf = (counts, severity) => counts.find(it => it.severity === severity)?.count ?? 0

/**
 * A count, linking to the findings it counts when there is any.
 */
function CountLink({project, filter, count, testId, label}) {
    if (count > 0) {
        return <Link href={projectFindingsUri(project, filter)} data-testid={testId} aria-label={label}>{count}</Link>
    } else {
        return <Typography.Text type="secondary" data-testid={testId} aria-label={label}>0</Typography.Text>
    }
}

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
    const hasFindings = summary && (summary.openCount + summary.acceptedCount + summary.resolvedCount) > 0

    const columns = [
        {
            key: 'branch',
            title: 'Branch',
            render: (_, {branch}) =>
                <Link href={projectFindingsUri(project, {branch: branch.name, state: 'OPEN'})}>{branch.name}</Link>,
        },
        ...FINDING_SEVERITIES.map(severity => ({
            key: severity,
            title: <FindingSeverityTag severity={severity}/>,
            align: 'right',
            render: (_, {branch, open}) =>
                <CountLink
                    project={project}
                    filter={{branch: branch.name, state: 'OPEN', severity}}
                    count={countOf(open, severity)}
                    testId={`security-branch-${branch.name}-${severity}`}
                    label={`${severityName(severity)} findings open on ${branch.name}`}
                />,
        })),
        {
            key: 'total',
            title: 'Open',
            align: 'right',
            render: (_, {branch, openCount}) =>
                <CountLink
                    project={project}
                    filter={{branch: branch.name, state: 'OPEN'}}
                    count={openCount}
                    testId={`security-branch-${branch.name}-total`}
                    label={`Findings open on ${branch.name}`}
                />,
        },
    ]

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
                    <Space size={24} wrap data-testid="security-open">
                        <Typography.Text strong>Open findings</Typography.Text>
                        {
                            FINDING_SEVERITIES.map(severity =>
                                <Space key={severity} size={4}>
                                    <FindingSeverityTag severity={severity}/>
                                    <CountLink
                                        project={project}
                                        filter={{state: 'OPEN', severity}}
                                        count={countOf(summary.open, severity)}
                                        testId={`security-open-${severity}`}
                                        label={`${severityName(severity)} open findings`}
                                    />
                                </Space>
                            )
                        }
                        <Space size={4}>
                            <Typography.Text type="secondary">Accepted</Typography.Text>
                            <CountLink
                                project={project}
                                filter={{state: 'ACCEPTED'}}
                                count={summary.acceptedCount}
                                testId="security-accepted"
                                label="Accepted findings"
                            />
                        </Space>
                        <Space size={4}>
                            <Typography.Text type="secondary">Resolved</Typography.Text>
                            <CountLink
                                project={project}
                                filter={{state: 'RESOLVED'}}
                                count={summary.resolvedCount}
                                testId="security-resolved"
                                label="Resolved findings"
                            />
                        </Space>
                    </Space>
                    {
                        exposedBranches.length > 0 ?
                            <Table
                                data-testid="security-branches"
                                size="small"
                                rowKey={it => it.branch.id}
                                columns={columns}
                                dataSource={exposedBranches}
                                pagination={false}
                            /> :
                            <Typography.Text type="secondary">No finding is open on any branch.</Typography.Text>
                    }
                </Space>
            }
        </PageSection>
    )
}
