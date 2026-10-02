import {useEffect} from "react";
import {gql} from "graphql-request";
import Link from "next/link";
import {Alert, Button, Empty, Input, Skeleton, Space, Tag, theme, Tooltip, Typography} from "antd";
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
    rankedFindingProjectsText,
    rankedFindingRows,
    rankedFindingsCaption,
    searchedFindingsCaption,
} from "@components/extension/scorecard/estates/estateFanOutModel";

/**
 * Number of ranked findings listed when nothing is searched, and of findings listed for a search
 */
const FINDINGS_LIST_SIZE = 20

export const gqlEstateRankedFindings = gql`
    query EstateRankedFindings($name: String!, $size: Int!) {
        estate(name: $name) {
            rankedFindings(size: $size) {
                externalId
                title
                severity
                openProjects
                acceptedProjects
                resolvedProjects
                firstSeen
            }
        }
    }
`

export const gqlEstateSearchedFindings = gql`
    query EstateSearchedFindings($name: String!, $text: String!, $size: Int!) {
        estate(name: $name) {
            searchedFindings(text: $text, size: $size) {
                externalId
                title
                severity
                openProjects
                acceptedProjects
                resolvedProjects
                firstSeen
            }
        }
    }
`

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
                        disabled
                    }
                    validationStamp {
                        id
                        name
                    }
                    since
                    state
                    counts
                    acceptanceExpiresAt
                }
            }
        }
    }
`

const DAY_FORMAT = "YYYY MMM DD"

/**
 * Why a branch exposing the finding does not count toward the state of the finding in its project.
 */
const notCountingReason = (branch) =>
    branch.disabled ?
        "Disabled branch: does not count toward the project's state" :
        "Outside the branch model: does not count toward the project's state"

/**
 * One branch a finding is exposed on, since the start of its exposure.
 *
 * A branch which does not count toward the state of the project is greyed, with a dashed border
 * rather than by its colour alone, and says why on hover and on keyboard focus — and to a screen
 * reader, in a text it alone reads.
 */
function FanOutBranch({project, branch, state, since, acceptanceExpiresAt, counts}) {
    const {token} = theme.useToken()
    const greyed = counts ? undefined : {color: token.colorTextSecondary}
    const tag =
        <Tag
            data-testid={`estate-fanout-branch-${project.name}-${branch.name}`}
            data-counts={counts ? 'true' : 'false'}
            style={counts ? undefined : {...greyed, borderStyle: 'dashed', background: 'transparent'}}
        >
            <Link href={branchUri(branch)} style={greyed}>{branch.name}</Link>
            {' '}
            <TimestampText value={since} prefix="since" format={DAY_FORMAT}/>
            {
                state === 'ACCEPTED' &&
                <>
                    {' · '}
                    {acceptanceExpiresAt ? `accepted until ${acceptanceExpiresAt}` : 'accepted without expiry'}
                </>
            }
            {
                !counts && <span className="ot-visually-hidden">{` (${notCountingReason(branch)})`}</span>
            }
        </Tag>
    return counts ?
        tag :
        <Tooltip title={notCountingReason(branch)} trigger={['hover', 'focus']}>{tag}</Tooltip>
}

/**
 * The branches a finding is exposed on, each since the start of its exposure — the ones which do
 * not count toward the state of the project after the ones which count — or when the finding was
 * resolved when it is exposed on none.
 */
function FanOutBranches({project, finding, branches}) {
    return (
        <span data-testid={`estate-fanout-branches-${project.name}`}>
            {
                branches.length > 0 ?
                    <Space size={4} wrap>
                        {
                            branches.map(exposure =>
                                <FanOutBranch key={exposure.branch.id} project={project} {...exposure}/>
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
 * Findings of an estate, one row per external ID, with the number of its projects where each is
 * open, accepted and resolved: a click on a row opens the fan-out of its finding.
 *
 * @param rows Rows of the findings, from `rankedFindingRows`
 * @param caption What the list shows
 * @param testId Prefix of the test IDs of the list, of its caption and of its rows
 * @param onSelect Called with the external ID of the finding whose row is clicked
 */
function FindingsRankTable({rows, caption, testId, onSelect}) {

    const columns = [
        {
            key: 'finding',
            title: 'Finding',
            render: (_, row) =>
                <Space orientation="vertical" size={0}>
                    {/* A button: the click stays on the page, which keeps the finding in its URL */}
                    <Button
                        type="link"
                        size="small"
                        style={{padding: 0, height: 'auto'}}
                        onClick={(event) => {
                            event.stopPropagation()
                            onSelect(row.externalId)
                        }}
                    >
                        <Typography.Text code>{row.externalId}</Typography.Text>
                    </Button>
                    <Typography.Text>{row.title}</Typography.Text>
                </Space>,
        },
        {
            key: 'severity',
            title: 'Severity',
            render: (_, row) => <FindingSeverityTag severity={row.severity}/>,
        },
        {
            key: 'projects',
            title: 'Projects',
            render: (_, row) =>
                <span data-testid={`${testId}-projects-${row.externalId}`} style={{whiteSpace: 'nowrap'}}>
                    {rankedFindingProjectsText(row)}
                </span>,
        },
        {
            key: 'firstSeen',
            title: 'First seen',
            render: (_, row) => <TimestampText value={row.firstSeen} relative={true}/>,
        },
    ]

    return (
        <Space orientation="vertical" size={8} style={{width: '100%'}}>
            <SecondaryText data-testid={`${testId}-findings-caption`}>{caption}</SecondaryText>
            <Table
                data-testid={`${testId}-findings`}
                size="small"
                rowKey="key"
                columns={columns}
                dataSource={rows}
                pagination={false}
                scroll={{x: 'max-content'}}
                onRow={row => ({
                    'data-testid': `${testId}-finding-${row.externalId}`,
                    onClick: () => onSelect(row.externalId),
                    style: {cursor: 'pointer'},
                })}
            />
        </Space>
    )
}

/**
 * The findings open in the most projects of the estate, one row per external ID: a click on a row
 * opens the fan-out of its finding.
 */
function RankedFindings({estate, onSelect}) {

    const {data: rankedFindings, finished, error} = useQuery(
        gqlEstateRankedFindings,
        {
            variables: {name: estate.name, size: FINDINGS_LIST_SIZE},
            deps: [estate.name],
            condition: true,
            dataFn: data => data.estate?.rankedFindings ?? [],
        }
    )

    if (!finished) {
        return <Skeleton active/>
    } else if (error) {
        return <Alert type="error" showIcon title={error}/>
    }

    const rows = rankedFindingRows(rankedFindings)
    if (rows.length === 0) {
        return <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description="No finding is open in the projects of this estate."
        />
    }

    return <FindingsRankTable
        rows={rows}
        caption={rankedFindingsCaption(rows.length, FINDINGS_LIST_SIZE)}
        testId="estate-ranked"
        onSelect={onSelect}
    />
}

/**
 * The findings of the estate whose external ID contains a text, ignoring case, open or not, one row
 * per external ID, ranked as the ranked findings are: a click on a row opens the fan-out of its
 * finding. When only one finding is found, its fan-out is opened right away.
 *
 * @param estate Estate, with its `name`
 * @param text Text searched among the external IDs
 * @param onSelect Called with the external ID of the finding whose row is clicked
 * @param onOnlyOne Called with the external ID of the only finding found
 */
function SearchedFindings({estate, text, onSelect, onOnlyOne}) {

    const {data: searchedFindings, finished, error} = useQuery(
        gqlEstateSearchedFindings,
        {
            variables: {name: estate.name, text, size: FINDINGS_LIST_SIZE},
            deps: [estate.name, text],
            condition: true,
            dataFn: data => data.estate?.searchedFindings ?? [],
        }
    )

    const rows = finished && !error ? rankedFindingRows(searchedFindings) : []
    const onlyOne = rows.length === 1 ? rows[0].externalId : null

    // Opening the fan-out is a change of the state of the page, kept by its parent: not while
    // rendering. Once per finding found, whatever the identity of the callback, recreated by each
    // render of the parent.
    useEffect(() => {
        if (onlyOne) {
            onOnlyOne(onlyOne)
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [onlyOne])

    if (!finished || onlyOne) {
        return <Skeleton active/>
    } else if (error) {
        return <Alert type="error" showIcon title={error}/>
    } else if (rows.length === 0) {
        return <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description={`No finding of this estate has an external ID containing "${text}".`}
        />
    }

    return <FindingsRankTable
        rows={rows}
        caption={searchedFindingsCaption(rows.length, FINDINGS_LIST_SIZE, text)}
        testId="estate-searched"
        onSelect={onSelect}
    />
}

/**
 * The findings fan-out of an estate: one finding, picked among the findings open in the most
 * projects of the estate, or among the ones whose external ID (a CVE, a rule ID) contains the text
 * searched, ignoring case, and the projects of the estate exposed to it, on which branches, since
 * when — among the projects the user can see and whose findings the user is granted the view of.
 *
 * The finding and the search are kept by the parent — the estate page keeps them in its URL, so
 * that a fan-out or a search can be shared by its link.
 *
 * @param estate Estate, with its `name`
 * @param finding External ID of the finding whose fan-out is shown, `null` for none
 * @param search Text searched among the external IDs, `null` for the ranked findings. Kept with the
 * finding picked among its results, to go back to them.
 * @param onChange Called with the new `{finding, search}`
 */
export default function EstateFindingsFanOut({estate, finding = null, search = null, onChange}) {

    const {data: findings, finished, error} = useQuery(
        gqlEstateFindingsFanOut,
        {
            variables: {name: estate.name, externalId: finding},
            deps: [estate.name, finding],
            condition: !!finding,
            dataFn: data => data.estate?.findings ?? [],
        }
    )

    let result
    if (finding) {
        if (!finished) {
            result = <Skeleton active/>
        } else if (error) {
            result = <Alert type="error" showIcon title={error}/>
        } else {
            result = <FanOutResult externalId={finding} findings={findings ?? []}/>
        }
    } else if (search) {
        result = <SearchedFindings
            // A new search is a new list: not the rows of the previous one while it loads
            key={search}
            estate={estate}
            text={search}
            onSelect={externalId => onChange({finding: externalId, search})}
            // The only finding found replaces its search: going back to it would open it again
            onOnlyOne={externalId => onChange({finding: externalId, search: null})}
        />
    } else {
        result = <RankedFindings estate={estate} onSelect={externalId => onChange({finding: externalId, search: null})}/>
    }

    return (
        <Space orientation="vertical" size={16} style={{width: '100%'}} data-testid="estate-fanout">
            <Space size={16} wrap>
                {/*
                  * `data-testid` on an antd 6 `Input.Search` lands on its `Space.Compact` wrapper,
                  * not on the `<input>`: a test types into the searchbox inside it.
                  *
                  * Keyed by what it shows, so that it says the finding shown when it is picked
                  * elsewhere than in it — a row of the findings, the URL.
                  */}
                <Input.Search
                    key={finding ?? search ?? ''}
                    data-testid="estate-fanout-search"
                    style={{maxWidth: 420}}
                    allowClear
                    enterButton="Search"
                    aria-label="External ID of the finding, or part of it"
                    placeholder="External ID or part of it, like CVE-2021-44228 or csrf"
                    defaultValue={finding ?? search ?? ''}
                    onSearch={value => onChange({finding: null, search: fanOutExternalId(value)})}
                />
                {
                    finding && search &&
                    <Button
                        type="link"
                        data-testid="estate-fanout-back-search"
                        onClick={() => onChange({finding: null, search})}
                    >
                        {`Findings containing "${search}"`}
                    </Button>
                }
                {
                    (finding || search) &&
                    <Button
                        type="link"
                        data-testid="estate-fanout-back"
                        onClick={() => onChange({finding: null, search: null})}
                    >
                        All open findings
                    </Button>
                }
            </Space>
            {
                !finding && !search &&
                <SecondaryText>
                    Search a finding by its external ID, like a CVE or a rule ID, or by a part of it, or pick one
                    below, to see the projects of this estate exposed to it, on which branches, and since when.
                </SecondaryText>
            }
            {result}
        </Space>
    )
}
