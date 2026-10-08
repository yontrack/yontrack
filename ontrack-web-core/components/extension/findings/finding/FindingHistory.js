import {useState} from "react";
import Link from "next/link";
import {gql} from "graphql-request";
import {Button, Empty, Skeleton, Space, Timeline, Typography} from "antd";
import {useQuery} from "@components/services/GraphQL";
import {branchUri, validationRunUri, validationStampUri} from "@components/common/Links";
import TimestampText from "@components/common/TimestampText";
import PeriodBuild from "@components/extension/findings/finding/PeriodBuild";
import FindingObservationsTimeline from "@components/extension/findings/finding/FindingObservationsTimeline";

const PAGE_SIZE = 20

const gqlFindingHistory = gql`
    query FindingHistory($id: Int!, $size: Int!) {
        finding(id: $id) {
            history(offset: 0, size: $size) {
                pageInfo {
                    totalSize
                }
                pageItems {
                    type
                    time
                    branch {
                        id
                        name
                    }
                    validationStamp {
                        id
                        name
                    }
                    validationRun {
                        id
                        runOrder
                        build {
                            id
                        }
                    }
                    build
                    resolutionReason
                    acceptance {
                        statement
                        expiresAt
                    }
                    count
                    firstTime
                    lastTime
                    firstBuild
                    lastBuild
                }
            }
        }
    }
`

/**
 * What each type of entry says, its colour on the timeline, and its title.
 */
const entryTypes = {
    DISCOVERED: {color: 'red', title: 'Discovered on'},
    EXPOSED: {color: 'red', title: 'Exposed on'},
    REOPENED: {color: 'red', title: 'Reopened on'},
    RESOLVED: {color: 'green', title: 'Fixed on'},
    ACCEPTED: {color: 'orange', title: 'Accepted on'},
    ACCEPTANCE_WITHDRAWN: {color: 'red', title: 'Acceptance withdrawn on'},
    ACCEPTANCE_EXPIRED: {color: 'red', title: 'Acceptance expired on'},
    OBSERVATIONS: {color: 'gray', title: null},
}

function Run({run, validationStamp}) {
    return run ?
        <Link href={validationRunUri(run)}>{validationStamp.name} #{run.runOrder}</Link> :
        <Link href={validationStampUri(validationStamp)}>{validationStamp.name}</Link>
}

function ObservationsGroup({findingId, entry}) {
    const [expanded, setExpanded] = useState(false)
    const {branch, validationStamp, count, firstTime, lastTime, firstBuild, lastBuild} = entry
    return (
        <Space orientation="vertical" size={4} style={{width: '100%'}} data-testid="finding-history-group">
            <Typography.Text type="secondary">
                Reported by {count} scan{count === 1 ? '' : 's'} on <Link href={branchUri(branch)}>{branch.name}</Link>
                {
                    firstBuild && lastBuild && firstBuild !== lastBuild &&
                    <>, builds <Typography.Text code>{firstBuild}</Typography.Text> → <Typography.Text
                        code>{lastBuild}</Typography.Text></>
                }
                {
                    firstBuild && firstBuild === lastBuild &&
                    <>, build <Typography.Text code>{firstBuild}</Typography.Text></>
                }
                {' '}
                <Button type="link" size="small" onClick={() => setExpanded(!expanded)} aria-expanded={expanded}>
                    {expanded ? 'Hide' : 'Show'}
                </Button>
            </Typography.Text>
            {
                expanded &&
                <FindingObservationsTimeline
                    id={findingId}
                    filter={{
                        // IDs come as strings from GraphQL, the filter takes integers
                        branchId: Number(branch.id),
                        validationStampId: Number(validationStamp.id),
                        from: firstTime,
                        to: lastTime,
                    }}
                />
            }
        </Space>
    )
}

function Entry({findingId, entry}) {
    if (entry.type === 'OBSERVATIONS') {
        return <ObservationsGroup findingId={findingId} entry={entry}/>
    }
    const {type, time, branch, validationStamp, validationRun, build, acceptance} = entry
    const {title} = entryTypes[type] ?? {title: type}
    return (
        <Space orientation="vertical" size={2} data-testid={`finding-history-${type}`}>
            <Typography.Text>
                <Typography.Text strong>
                    {title} <Link href={branchUri(branch)}>{branch.name}</Link>
                </Typography.Text>
                {
                    type !== 'ACCEPTANCE_EXPIRED' &&
                    <>
                        {' '}in build <PeriodBuild run={validationRun} name={build}/>
                    </>
                }
                {
                    type === 'RESOLVED' ?
                        <>, no longer reported by <Run run={validationRun} validationStamp={validationStamp}/></> :
                        type !== 'ACCEPTANCE_EXPIRED' &&
                        <>, reported by <Run run={validationRun} validationStamp={validationStamp}/></>
                }
                {
                    acceptance?.expiresAt && type === 'ACCEPTED' &&
                    <>, until {acceptance.expiresAt}</>
                }
                {
                    acceptance?.expiresAt && type === 'ACCEPTANCE_EXPIRED' &&
                    <>, after {acceptance.expiresAt}</>
                }
            </Typography.Text>
            {
                type === 'ACCEPTED' && acceptance?.statement &&
                <Typography.Text italic>{acceptance.statement}</Typography.Text>
            }
            <Typography.Text type="secondary"><TimestampText value={time}/></Typography.Text>
        </Space>
    )
}

/**
 * The history of a finding, the most recent first: its discovery, its exposure on other branches,
 * its fixes and its reopenings, with the builds, the changes of its acceptance, and its
 * observations in between, grouped, which expand on demand. One page at first, more on demand.
 */
export default function FindingHistory({id}) {

    const [size, setSize] = useState(PAGE_SIZE)

    const {data, loading, finished, error} = useQuery(
        gqlFindingHistory,
        {
            variables: {id, size},
            deps: [id, size],
            condition: !!id,
            dataFn: data => data.finding?.history,
        }
    )

    const entries = data?.pageItems ?? []
    const totalSize = data?.pageInfo?.totalSize ?? 0

    if (error) {
        return <Typography.Text type="danger">{error}</Typography.Text>
    }

    return (
        <Skeleton active loading={!data && (loading || !finished)}>
            {
                entries.length === 0 &&
                <Empty
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description="No history is kept for this finding"
                />
            }
            {
                entries.length > 0 &&
                <Space orientation="vertical" style={{width: '100%'}}>
                    <Timeline
                        data-testid="finding-history"
                        items={entries.map((entry, index) => ({
                            key: `${entry.type}-${entry.branch.id}-${entry.validationStamp.id}-${entry.time}-${index}`,
                            color: entryTypes[entry.type]?.color ?? 'gray',
                            content: <Entry findingId={id} entry={entry}/>,
                        }))}
                    />
                    {
                        entries.length < totalSize &&
                        <Button loading={loading} onClick={() => setSize(size + PAGE_SIZE)}>
                            More history
                        </Button>
                    }
                </Space>
            }
        </Skeleton>
    )
}
