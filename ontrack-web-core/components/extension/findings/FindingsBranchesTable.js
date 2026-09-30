import Link from "next/link";
import Table from "@components/common/table/Table";
import {projectFindingsUri} from "@components/common/Links";
import FindingSeverityTag from "@components/extension/findings/FindingSeverityTag";
import FindingSeverityCountTag from "@components/extension/findings/FindingSeverityCountTag";
import {FindingsCountLink} from "@components/extension/findings/FindingsSummaryCounts";
import {FINDING_SEVERITIES, severityCount} from "@components/extension/findings/findingsModel";

/**
 * Exposure of the findings of a project per branch: a row per branch, its open findings by
 * severity, each count linking to the findings page filtered on the branch and the severity.
 *
 * @param project Project, with its `id`
 * @param branches `{branch, open, openCount}` per branch
 * @param testIdPrefix Prefix of the test IDs of the table and its counts
 */
export default function FindingsBranchesTable({project, branches, testIdPrefix}) {

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
                <FindingSeverityCountTag
                    severity={severity}
                    count={severityCount(open, severity)}
                    href={projectFindingsUri(project, {branch: branch.name, state: 'OPEN', severity})}
                    short={true}
                    testId={`${testIdPrefix}-branch-${branch.name}-${severity}`}
                />,
        })),
        {
            key: 'total',
            title: 'Open',
            align: 'right',
            render: (_, {branch, openCount}) =>
                <FindingsCountLink
                    project={project}
                    filter={{branch: branch.name, state: 'OPEN'}}
                    count={openCount}
                    testId={`${testIdPrefix}-branch-${branch.name}-total`}
                    label={`Findings open on ${branch.name}`}
                />,
        },
    ]

    return (
        <Table
            data-testid={`${testIdPrefix}-branches`}
            size="small"
            rowKey={it => it.branch.id}
            columns={columns}
            dataSource={branches}
            pagination={false}
        />
    )
}
