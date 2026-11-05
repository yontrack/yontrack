import Link from "next/link";
import {gql} from "graphql-request";
import {Empty, Space, Tag, theme, Tooltip, Typography} from "antd";
import Table from "@components/common/table/Table";
import {branchUri, findingUri, projectUri} from "@components/common/Links";
import TimestampText from "@components/common/TimestampText";
import SecondaryText from "@components/extension/scorecard/SecondaryText";
import FindingSeverityTag from "@components/extension/findings/FindingSeverityTag";
import FindingStateTag from "@components/extension/findings/FindingStateTag";
import {kindName} from "@components/extension/findings/findingsModel";
import {fanOutRows, fanOutSummary} from "@components/extension/findings/findingFanOutModel";

/**
 * What the fan-out of a finding reads of each of its findings.
 */
export const gqlFindingFanOutFields = gql`
    fragment FindingFanOutFields on Finding {
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
            counts
            acceptanceExpiresAt
            resolvedAt
        }
    }
`

const DAY_FORMAT = "YYYY MMM DD"

/**
 * Why a branch exposing the finding does not count toward the state of the finding in its project.
 * The server gives no disabled branch.
 */
const NOT_COUNTING_REASON = "Outside the branch model: does not count toward the project's state"

/**
 * One branch of a finding: exposed or accepted since the start of its exposure, or resolved, with
 * its date.
 *
 * A branch which does not count toward the state of the project is greyed, with a dashed border
 * rather than by its colour alone, and says why on hover and on keyboard focus — and to a screen
 * reader, in a text it alone reads.
 */
function FanOutBranch({project, branch, state, since, resolvedAt, acceptanceExpiresAt, counts, testId}) {
    const {token} = theme.useToken()
    const greyed = counts ? undefined : {color: token.colorTextSecondary}
    const tag =
        <Tag
            data-testid={`${testId}-branch-${project.name}-${branch.name}`}
            data-counts={counts ? 'true' : 'false'}
            style={counts ? undefined : {...greyed, borderStyle: 'dashed', background: 'transparent'}}
        >
            <Link href={branchUri(branch)} style={greyed}>{branch.name}</Link>
            {' '}
            {
                state === 'RESOLVED' ?
                    <TimestampText value={resolvedAt} prefix="Resolved" format={DAY_FORMAT}/> :
                    <TimestampText value={since} prefix="since" format={DAY_FORMAT}/>
            }
            {
                state === 'ACCEPTED' &&
                <>
                    {' · '}
                    {acceptanceExpiresAt ? `accepted until ${acceptanceExpiresAt}` : 'accepted without expiry'}
                </>
            }
            {
                !counts && <span className="ot-visually-hidden">{` (${NOT_COUNTING_REASON})`}</span>
            }
        </Tag>
    return counts ?
        tag :
        <Tooltip title={NOT_COUNTING_REASON} trigger={['hover', 'focus']}>{tag}</Tooltip>
}

/**
 * The branches of a finding: the ones exposing it, each since the start of its exposure, then the
 * ones where it is resolved, each with its date — the ones which do not count toward the state of
 * the project after the ones which count. When it has none left, its branches having been deleted,
 * when the finding was resolved.
 */
function FanOutBranches({project, finding, branches, testId}) {
    return (
        <span data-testid={`${testId}-branches-${project.name}`}>
            {
                branches.length > 0 ?
                    <Space size={4} wrap>
                        {
                            branches.map(exposure =>
                                <FanOutBranch key={exposure.branch.id} project={project} testId={testId} {...exposure}/>
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
 * The projects reporting one finding, by its external ID: its title, a summary, and one row per
 * finding, grouped by project.
 *
 * @param findings The findings of the external ID, as `gqlFindingFanOutFields` reads them
 * @param summaryText Gets the summary of the findings, from `fanOutSummary`, and gives what it says
 * @param emptyDescription What to say when no project reports it
 * @param testId Prefix of the test IDs
 */
export default function FindingFanOutTable({findings, summaryText, emptyDescription, testId}) {

    if (findings.length === 0) {
        return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={emptyDescription}/>
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
                <Link href={projectUri(project)} data-testid={`${testId}-project-${project.name}`}>
                    {project.name}
                </Link>,
        },
        {
            key: 'state',
            title: 'State',
            render: (_, {project, finding}) =>
                <span data-testid={`${testId}-state-${project.name}`}>
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
            title: 'Branches',
            render: (_, {project, finding, branches}) =>
                <FanOutBranches project={project} finding={finding} branches={branches} testId={testId}/>,
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
                    <span data-testid={`${testId}-title`}>
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
                <SecondaryText data-testid={`${testId}-summary`}>{summaryText(summary)}</SecondaryText>
            </Space>
            <Table
                data-testid={`${testId}-table`}
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
