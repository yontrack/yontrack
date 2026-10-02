import {useState} from "react";
import {gql} from "graphql-request";
import Link from "next/link";
import {Alert, Empty, Input, Skeleton, Space, Tag, Typography} from "antd";
import Table from "@components/common/table/Table";
import {useQuery} from "@components/services/GraphQL";
import {branchUri, findingUri, projectUri} from "@components/common/Links";
import TimestampText from "@components/common/TimestampText";
import SecondaryText from "@components/extension/scorecard/SecondaryText";
import FindingSeverityTag from "@components/extension/findings/FindingSeverityTag";
import FindingStateTag from "@components/extension/findings/FindingStateTag";
import {kindName} from "@components/extension/findings/findingsModel";
import {
    fanOutExternalId,
    fanOutRows,
    fanOutSummary,
} from "@components/extension/scorecard/estates/estateFanOutModel";

export const gqlEstateFindingsFanOut = gql`
    query EstateFindingsFanOut($name: String!, $externalId: String!) {
        estate(name: $name) {
            findings(externalId: $externalId) {
                id
                project {
                    id
                    name
                }
                externalId
                location
                scanner
                kind
                title
                url
                maxSeverity
                state
                firstSeen
                resolvedAt
                exposures {
                    branch {
                        id
                        name
                    }
                    validationStamp {
                        id
                        name
                    }
                    since
                    state
                    acceptanceExpiresAt
                }
            }
        }
    }
`

const DAY_FORMAT = "YYYY MMM DD"

/**
 * The branches a finding is exposed on, each since the start of its exposure, or when the finding
 * was resolved when it is exposed on none.
 */
function FanOutBranches({project, finding, branches}) {
    return (
        <span data-testid={`estate-fanout-branches-${project.name}`}>
            {
                branches.length > 0 ?
                    <Space size={4} wrap>
                        {
                            branches.map(({branch, state, since, acceptanceExpiresAt}) =>
                                <Tag key={branch.id} data-testid={`estate-fanout-branch-${project.name}-${branch.name}`}>
                                    <Link href={branchUri(branch)}>{branch.name}</Link>
                                    {' '}
                                    <TimestampText value={since} prefix="since" format={DAY_FORMAT}/>
                                    {
                                        state === 'ACCEPTED' &&
                                        <>
                                            {' · '}
                                            {acceptanceExpiresAt ? `accepted until ${acceptanceExpiresAt}` : 'accepted without expiry'}
                                        </>
                                    }
                                </Tag>
                            )
                        }
                    </Space> :
                    <SecondaryText>
                        {
                            finding.resolvedAt ?
                                <TimestampText value={finding.resolvedAt} prefix="Resolved" format={DAY_FORMAT}/> :
                                'No branch'
                        }
                    </SecondaryText>
            }
        </span>
    )
}

/**
 * `2 projects of this estate report CVE-1: exposed in 1, accepted in 0, resolved in 1`
 */
const summaryText = (externalId, {projects, exposed, accepted, resolved}) =>
    `${projects} ${projects === 1 ? 'project of this estate reports' : 'projects of this estate report'} ${externalId}: ` +
    `exposed in ${exposed}, accepted in ${accepted}, resolved in ${resolved}`

/**
 * The projects of an estate reporting one finding, by its external ID.
 */
function FanOutResult({externalId, findings}) {

    if (findings.length === 0) {
        return <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description={`No project of this estate reports ${externalId}.`}
        />
    }

    const rows = fanOutRows(findings)
    const summary = fanOutSummary(findings)
    // The external ID gives the same weakness whatever the project: its title and link are the
    // ones of its most exposed finding
    const reference = rows[0].finding

    const columns = [
        {
            key: 'project',
            title: 'Project',
            onCell: (row) => ({rowSpan: row.projectRowSpan}),
            render: (_, {project}) =>
                <Link href={projectUri(project)} data-testid={`estate-fanout-project-${project.name}`}>
                    {project.name}
                </Link>,
        },
        {
            key: 'state',
            title: 'State',
            render: (_, {project, finding}) =>
                <span data-testid={`estate-fanout-state-${project.name}`}>
                    <FindingStateTag state={finding.state}/>
                </span>,
        },
        {
            key: 'severity',
            title: 'Severity',
            render: (_, {finding}) => <FindingSeverityTag severity={finding.maxSeverity}/>,
        },
        {
            key: 'finding',
            title: 'Location',
            render: (_, {finding}) =>
                <Space orientation="vertical" size={0}>
                    <Link href={findingUri(finding)}>
                        <Typography.Text code style={{wordBreak: 'break-all'}}>
                            {finding.location || 'No location'}
                        </Typography.Text>
                    </Link>
                    <SecondaryText style={{fontSize: 12}}>{finding.scanner} · {kindName(finding.kind)}</SecondaryText>
                </Space>,
        },
        {
            key: 'branches',
            title: 'Exposed on',
            render: (_, {project, finding, branches}) =>
                <FanOutBranches project={project} finding={finding} branches={branches}/>,
        },
        {
            key: 'firstSeen',
            title: 'First seen',
            render: (_, {finding}) => <TimestampText value={finding.firstSeen} relative={true}/>,
        },
    ]

    return (
        <Space orientation="vertical" size={16} style={{width: '100%'}}>
            <Space orientation="vertical" size={4}>
                <Space size={8} wrap>
                    <span data-testid="estate-fanout-title">
                        <Typography.Text code strong>{reference.externalId}</Typography.Text>
                        <Typography.Text strong>{reference.title}</Typography.Text>
                    </span>
                    {
                        reference.url &&
                        <Typography.Link href={reference.url} target="_blank" rel="noopener noreferrer">
                            More information
                        </Typography.Link>
                    }
                </Space>
                <SecondaryText data-testid="estate-fanout-summary">{summaryText(externalId, summary)}</SecondaryText>
            </Space>
            <Table
                data-testid="estate-fanout-table"
                size="small"
                rowKey="key"
                columns={columns}
                dataSource={rows}
                pagination={false}
                scroll={{x: 'max-content'}}
            />
        </Space>
    )
}

/**
 * The findings fan-out of an estate: one finding, searched by its external ID (a CVE, a rule ID),
 * and the projects of the estate exposed to it, on which branches, since when — among the projects
 * the user can see and whose findings the user is granted the view of.
 *
 * @param estate Estate, with its `name`
 */
export default function EstateFindingsFanOut({estate}) {

    const [externalId, setExternalId] = useState(null)

    const {data: findings, finished, error} = useQuery(
        gqlEstateFindingsFanOut,
        {
            variables: {name: estate.name, externalId},
            deps: [estate.name, externalId],
            condition: !!externalId,
            dataFn: data => data.estate?.findings ?? [],
        }
    )

    let result
    if (!externalId) {
        result = <SecondaryText>
            Search a finding by its external ID, like a CVE or a rule ID, to see the projects of this estate
            exposed to it, on which branches, and since when.
        </SecondaryText>
    } else if (!finished) {
        result = <Skeleton active/>
    } else if (error) {
        result = <Alert type="error" showIcon title={error}/>
    } else {
        result = <FanOutResult externalId={externalId} findings={findings ?? []}/>
    }

    return (
        <Space orientation="vertical" size={16} style={{width: '100%'}} data-testid="estate-fanout">
            {/*
              * `data-testid` on an antd 6 `Input.Search` lands on its `Space.Compact` wrapper,
              * not on the `<input>`: a test types into the searchbox inside it.
              */}
            <Input.Search
                data-testid="estate-fanout-search"
                style={{maxWidth: 420}}
                allowClear
                enterButton="Search"
                aria-label="External ID of the finding"
                placeholder="External ID, like CVE-2021-44228"
                onSearch={value => setExternalId(fanOutExternalId(value))}
            />
            {result}
        </Space>
    )
}
