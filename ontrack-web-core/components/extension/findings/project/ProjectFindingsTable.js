import Link from "next/link";
import {Space, Tag, Typography} from "antd";
import Table from "@components/common/table/Table";
import {branchUri, findingUri} from "@components/common/Links";
import FindingSeverityTag from "@components/extension/findings/FindingSeverityTag";
import FindingStateTag from "@components/extension/findings/FindingStateTag";
import TimestampText from "@components/common/TimestampText";
import {
    DEFAULT_FINDINGS_SORT,
    exposedBranches,
    EXPOSED_FOR_FINDINGS_SORT,
    kindName,
} from "@components/extension/findings/findingsModel";
import FindingExposedFor from "@components/extension/findings/project/FindingExposedFor";

/**
 * One page of the findings of a project.
 *
 * The order is the one of the server: the *Exposed for* column only says whether the findings are
 * sorted by it, the longest exposed first, and asks for it (`onSortChange`, with `EXPOSED_FOR` or
 * `DEFAULT`).
 */
export default function ProjectFindingsTable({
                                                 findings,
                                                 totalSize,
                                                 loading,
                                                 current,
                                                 pageSize,
                                                 onPageChange,
                                                 sort = DEFAULT_FINDINGS_SORT,
                                                 onSortChange,
                                             }) {

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
            key: 'exposedFor',
            title: 'Exposed for',
            sorter: true,
            sortDirections: ['descend'],
            sortOrder: sort === EXPOSED_FOR_FINDINGS_SORT ? 'descend' : null,
            showSorterTooltip: {title: 'Sort by exposure, the longest exposed first'},
            render: (_, finding) => <FindingExposedFor finding={finding}/>,
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
            onChange={(_pagination, _filters, sorter, {action}) => {
                if (action === 'sort' && onSortChange) {
                    onSortChange(sorter?.order ? EXPOSED_FOR_FINDINGS_SORT : DEFAULT_FINDINGS_SORT)
                }
            }}
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
