import Link from "next/link";
import {gql} from "graphql-request";
import {Empty, Space, Table, Typography} from "antd";
import {useQuery} from "@components/services/GraphQL";
import {findingUri} from "@components/common/Links";
import FindingSeverityTag from "@components/extension/findings/FindingSeverityTag";
import FindingStateTag from "@components/extension/findings/FindingStateTag";
import {FindingAcceptanceTag} from "@components/extension/findings/finding/FindingAcceptance";

const gqlValidationRunFindings = gql`
    query ValidationRunFindings($id: Int!) {
        validationRuns(id: $id) {
            findings {
                severity
                rawSeverity
                installedVersion
                fixedVersion
                acceptance {
                    effective
                    statement
                    expiresAt
                }
                finding {
                    id
                    externalId
                    title
                    location
                    scanner
                    kind
                    state
                }
            }
        }
    }
`

/**
 * The findings reported by one security scan — a validation run of the `security-findings` data
 * type — each with what this scan observed: the severity, the versions, the acceptance. The most
 * severe first, as the server gives them.
 *
 * The caller shows it only to a user granted the view of the findings of the project; the server
 * returns none to any other one anyway.
 */
export default function ValidationRunFindings({run}) {

    const {data: findings, loading, finished, error} = useQuery(
        gqlValidationRunFindings,
        {
            variables: {id: Number(run.id)},
            deps: [run.id],
            condition: !!run.id,
            dataFn: data => data.validationRuns?.[0]?.findings ?? [],
        }
    )

    const columns = [
        {
            key: 'severity',
            title: 'Severity',
            render: (_, {severity, rawSeverity}) =>
                <span title={rawSeverity ? `Reported as ${rawSeverity}` : undefined}>
                    <FindingSeverityTag severity={severity}/>
                </span>,
        },
        {
            key: 'finding',
            title: 'Finding',
            render: (_, {finding}) =>
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
            render: (_, {finding}) =>
                finding.location ?
                    <Typography.Text code style={{wordBreak: 'break-all'}}>{finding.location}</Typography.Text> :
                    <Typography.Text type="secondary">None</Typography.Text>,
        },
        {
            key: 'versions',
            title: 'Installed / fixed',
            render: (_, {installedVersion, fixedVersion}) =>
                <Space orientation="vertical" size={0}>
                    {
                        installedVersion ?
                            <Typography.Text code>{installedVersion}</Typography.Text> :
                            <Typography.Text type="secondary">-</Typography.Text>
                    }
                    {
                        fixedVersion ?
                            <Typography.Text code>{fixedVersion}</Typography.Text> :
                            <Typography.Text type="secondary">No fix known</Typography.Text>
                    }
                </Space>,
        },
        {
            key: 'acceptance',
            title: 'Acceptance',
            render: (_, {acceptance}) =>
                acceptance ?
                    <FindingAcceptanceTag acceptance={acceptance}/> :
                    <Typography.Text type="secondary">None</Typography.Text>,
        },
        {
            key: 'state',
            title: 'State in the project',
            render: (_, {finding}) => <FindingStateTag state={finding.state}/>,
        },
    ]

    if (error) {
        return <Typography.Text type="danger">{error}</Typography.Text>
    }

    return (
        <Table
            data-testid="validation-run-findings"
            size="small"
            rowKey={it => it.finding.id}
            loading={loading || !finished}
            columns={columns}
            dataSource={findings ?? []}
            locale={{
                emptyText: <Empty
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description="This scan reported no finding"
                />,
            }}
            pagination={{
                pageSize: 20,
                hideOnSinglePage: true,
                showTotal: (total) => `${total} finding${total === 1 ? '' : 's'}`,
            }}
        />
    )
}
