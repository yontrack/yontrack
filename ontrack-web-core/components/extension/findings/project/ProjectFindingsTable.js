import Link from "next/link";
import {Space, Table, Tag, Typography} from "antd";
import {branchUri, findingUri} from "@components/common/Links";
import FindingSeverityTag from "@components/extension/findings/FindingSeverityTag";
import FindingStateTag from "@components/extension/findings/FindingStateTag";
import TimestampText from "@components/common/TimestampText";
import {exposedBranches, kindName} from "@components/extension/findings/findingsModel";

/**
 * One page of the findings of a project.
 */
export default function ProjectFindingsTable({findings, totalSize, loading, current, pageSize, onPageChange}) {

    const columns = [
        {
            key: 'severity',
            title: 'Severity',
            render: (_, finding) => <FindingSeverityTag severity={finding.maxSeverity}/>,
        },
        {
            key: 'finding',
            title: 'Finding',
            render: (_, finding) =>
                <Space orientation="vertical" size={0}>
                    <Link href={findingUri(finding)}>
                        <Typography.Text code>{finding.externalId}</Typography.Text>
                    </Link>
                    <Typography.Text type="secondary">{finding.title}</Typography.Text>
                </Space>,
        },
        {
            key: 'location',
            title: 'Location',
            render: (_, finding) =>
                finding.location ?
                    <Typography.Text code style={{wordBreak: 'break-all'}}>{finding.location}</Typography.Text> :
                    <Typography.Text type="secondary">None</Typography.Text>,
        },
        {
            key: 'scanner',
            title: 'Scanner',
            dataIndex: 'scanner',
        },
        {
            key: 'kind',
            title: 'Kind',
            render: (_, finding) => kindName(finding.kind),
        },
        {
            key: 'state',
            title: 'State',
            render: (_, finding) => <FindingStateTag state={finding.state}/>,
        },
        {
            key: 'exposure',
            title: 'Exposed on',
            render: (_, finding) => {
                const branches = exposedBranches(finding.exposures)
                return branches.length > 0 ?
                    <Space size={4} wrap>
                        {
                            branches.map(({branch, state}) =>
                                <Tag key={branch.id} title={state === 'ACCEPTED' ? 'Accepted on this branch' : undefined}>
                                    <Link href={branchUri(branch)}>{branch.name}</Link>
                                    {state === 'ACCEPTED' && ' (accepted)'}
                                </Tag>
                            )
                        }
                    </Space> :
                    <Typography.Text type="secondary">No branch</Typography.Text>
            },
        },
        {
            key: 'lastSeen',
            title: 'Last seen',
            render: (_, finding) => <TimestampText value={finding.lastSeen} relative={true}/>,
        },
    ]

    return (
        <Table
            data-testid="project-findings"
            size="small"
            rowKey="id"
            loading={loading}
            columns={columns}
            dataSource={findings}
            locale={{emptyText: 'No finding matches this filter'}}
            pagination={{
                current,
                pageSize,
                total: totalSize,
                showSizeChanger: true,
                showTotal: (total) => `${total} finding${total === 1 ? '' : 's'}`,
                onChange: onPageChange,
            }}
        />
    )
}
