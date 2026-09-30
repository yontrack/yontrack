import {Space, Typography} from "antd";
import FindingsSummaryCounts from "@components/extension/findings/FindingsSummaryCounts";
import FindingsBranchesTable from "@components/extension/findings/FindingsBranchesTable";

/**
 * Content of a findings widget, for a project or a branch: the open findings by severity, the
 * accepted and resolved ones, and optionally their exposure per branch.
 *
 * A summary the user cannot see says so, rather than showing zeros which would read as "clean".
 *
 * @param project Project of the findings, with its `id`
 * @param summary Summary of the findings, `null` for a user who cannot see them
 * @param reported Whether any finding has ever been reported for what the widget shows
 * @param branch Name of the branch the summary is about, for a branch widget
 * @param branches Branches to list, for a project widget displaying them
 * @param hint Line displayed above the counts, if any
 * @param testIdPrefix Prefix of the test IDs
 */
export default function FindingsWidgetPanel({project, summary, reported, branch, branches, hint, testIdPrefix}) {
    if (!summary) {
        return <Typography.Text type="secondary">You cannot see the findings of this project</Typography.Text>
    } else if (!reported) {
        return <Typography.Text type="secondary">No findings reported</Typography.Text>
    }
    return (
        <Space orientation="vertical" size={12} className="ot-line">
            {hint && <Typography.Text type="warning">{hint}</Typography.Text>}
            <FindingsSummaryCounts
                project={project}
                summary={summary}
                branch={branch}
                testIdPrefix={testIdPrefix}
            />
            {
                summary.openCount === 0 &&
                <Typography.Text type="success">No open findings</Typography.Text>
            }
            {
                branches && branches.length > 0 &&
                <FindingsBranchesTable
                    project={project}
                    branches={branches}
                    testIdPrefix={testIdPrefix}
                />
            }
        </Space>
    )
}
