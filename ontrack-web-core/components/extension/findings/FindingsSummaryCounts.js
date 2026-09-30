import Link from "next/link";
import {Space, Typography} from "antd";
import {projectFindingsUri} from "@components/common/Links";
import FindingSeverityCountTag from "@components/extension/findings/FindingSeverityCountTag";
import {FINDING_SEVERITIES, severityCount} from "@components/extension/findings/findingsModel";

/**
 * A count, linking to the findings it counts when there is any.
 */
export function FindingsCountLink({project, filter, count, testId, label}) {
    if (count > 0) {
        return <Link href={projectFindingsUri(project, filter)} data-testid={testId} aria-label={label}>{count}</Link>
    } else {
        return <Typography.Text type="secondary" data-testid={testId} aria-label={label}>0</Typography.Text>
    }
}

/**
 * The open findings by severity, as severity count tags, then the accepted and resolved ones, each
 * count linking to the findings page filtered on it.
 *
 * @param project Project of the findings, with its `id`
 * @param summary `{open, acceptedCount, resolvedCount}` — of a project or of a branch
 * @param branch Name of the branch the summary is about, if any: the links filter on it
 * @param testIdPrefix Prefix of the test IDs of the counts
 * @param title Text before the counts, if any
 */
export default function FindingsSummaryCounts({project, summary, branch, testIdPrefix, title}) {
    const filter = branch ? {branch} : {}
    return (
        <Space size={[12, 8]} wrap data-testid={`${testIdPrefix}-open`}>
            {title && <Typography.Text strong>{title}</Typography.Text>}
            <Space size={4} wrap>
                {
                    FINDING_SEVERITIES.map(severity =>
                        <FindingSeverityCountTag
                            key={severity}
                            severity={severity}
                            count={severityCount(summary.open, severity)}
                            href={projectFindingsUri(project, {...filter, state: 'OPEN', severity})}
                            testId={`${testIdPrefix}-open-${severity}`}
                        />
                    )
                }
            </Space>
            <Space size={4}>
                <Typography.Text type="secondary">Accepted</Typography.Text>
                <FindingsCountLink
                    project={project}
                    filter={{...filter, state: 'ACCEPTED'}}
                    count={summary.acceptedCount}
                    testId={`${testIdPrefix}-accepted`}
                    label="Accepted findings"
                />
            </Space>
            <Space size={4}>
                <Typography.Text type="secondary">Resolved</Typography.Text>
                <FindingsCountLink
                    project={project}
                    filter={{...filter, state: 'RESOLVED'}}
                    count={summary.resolvedCount}
                    testId={`${testIdPrefix}-resolved`}
                    label="Resolved findings"
                />
            </Space>
        </Space>
    )
}
