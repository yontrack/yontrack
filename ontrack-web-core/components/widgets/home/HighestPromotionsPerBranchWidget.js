import {useContext, useEffect, useState} from "react";
import {gql} from "graphql-request";
import Link from "next/link";
import {Alert, Empty, Flex, Popover, Segmented, Space, Table, theme, Typography} from "antd";
import {DashboardWidgetCellContext} from "@components/dashboards/DashboardWidgetCellContextProvider";
import {useQueries} from "@components/services/GraphQL";
import LoadingContainer from "@components/common/LoadingContainer";
import ProjectLink from "@components/projects/ProjectLink";
import BranchLink from "@components/branches/BranchLink";
import BuildLink from "@components/builds/BuildLink";
import Timestamp from "@components/common/Timestamp";
import PromotionRun from "@components/promotionRuns/PromotionRun";
import {PromotionLevelImage} from "@components/promotionLevels/PromotionLevelImage";
import {promotionRunUri} from "@components/common/Links";
import {getLocalHighestPromotionsFormat, setLocalHighestPromotionsFormat} from "@components/storage/local";
import {
    DEFAULT_HIGHEST_PROMOTIONS_FORMAT,
    DEFAULT_HIGHEST_PROMOTIONS_SHOW_PROJECT,
    DEFAULT_HIGHEST_PROMOTIONS_SORT,
    DEFAULT_PATTERN_COUNT,
    entryKind,
    HIGHEST_PROMOTIONS_FORMATS,
    highestPromotionsRows,
    promotionLadder,
    sortHighestPromotionsRows,
} from "@components/widgets/home/highestPromotionsPerBranch";

const DEFAULT_TITLE = "Highest promotions per branch"

const gqlBranchFields = `
    id
    name
    project {
        id
        name
    }
    promotionLevels {
        id
        name
        image
        description
    }
    builds(count: 1) {
        id
        name
        creation {
            time
        }
        releaseProperty {
            value
        }
        promotionRuns(lastPerLevel: true) {
            id
            creation {
                time
            }
            promotionLevel {
                id
                name
                image
                description
            }
        }
    }
`

const gqlBranchByName = gql`
    query HighestPromotionsPerBranchByName($project: String!, $branch: String!) {
        branchByName(project: $project, name: $branch) {
            ${gqlBranchFields}
        }
    }
`

const gqlBranchesByPattern = gql`
    query HighestPromotionsPerBranchByPattern($project: String!, $pattern: String!, $count: Int!) {
        projects(name: $project) {
            branches(name: $pattern, order: true, enabled: true, count: $count) {
                ${gqlBranchFields}
            }
        }
    }
`

/**
 * The query resolving one entry of the configuration, or `undefined` for an entry naming neither a
 * branch nor a pattern - there is nothing to look for.
 */
const entryQuery = entry => {
    switch (entryKind(entry)) {
        case 'BRANCH':
            return {
                query: gqlBranchByName,
                variables: {project: entry.project, branch: entry.branch},
                branches: data => data.branchByName ? [data.branchByName] : [],
            }
        case 'PATTERN':
            return {
                query: gqlBranchesByPattern,
                variables: {project: entry.project, pattern: entry.pattern, count: entry.count ?? DEFAULT_PATTERN_COUNT},
                branches: data => data.projects?.[0]?.branches ?? [],
            }
        default:
            return undefined
    }
}

function EntryName({entry}) {
    return <Typography.Text>{entry.project} / {entry.branch ?? entry.pattern}</Typography.Text>
}

function BranchName({branch, showProject}) {
    return (
        <Space>
            {
                showProject &&
                <>
                    <ProjectLink project={branch.project}/>
                    <span>/</span>
                </>
            }
            <BranchLink branch={branch}/>
        </Space>
    )
}

/**
 * The branch of a row, or the entry of the configuration for a row with no branch to link to - which
 * keeps its project, whether it is shown or not.
 */
function RowName({row, showProject}) {
    return row.branch ? <BranchName branch={row.branch} showProject={showProject}/> : <EntryName entry={row.entry}/>
}

/**
 * What a row with nothing to show says instead.
 */
function EmptyRowMessage({row}) {
    switch (row.type) {
        case 'NO_BUILD':
            return <Typography.Text type="secondary">No build yet</Typography.Text>
        case 'NO_MATCH':
            return <Typography.Text type="secondary">
                No branch of <Typography.Text code>{row.entry.project}</Typography.Text> matches <Typography.Text
                code>{row.entry.pattern}</Typography.Text>
            </Typography.Text>
        default:
            return <Typography.Text type="secondary">Branch not found</Typography.Text>
    }
}

function NotPromotedYet() {
    return <Typography.Text type="secondary">Not promoted yet</Typography.Text>
}

function LatestBuild({build}) {
    return (
        <Space>
            <BuildLink build={build}/>
            <Timestamp value={build.creation.time}/>
        </Space>
    )
}

function HighestPromotionsTable({rows, showProject}) {
    // The message of a row with nothing to show takes the place of both the build and its promotion
    const emptyRowCell = (row, span) => ({colSpan: row.type === 'BUILD' ? 1 : span})
    return (
        <Table
            size="small"
            dataSource={rows}
            rowKey="key"
            pagination={false}
            onRow={row => ({'data-testid': `highest-promotions-row-${row.key}`})}
            columns={[
                {
                    key: 'branch',
                    title: 'Branch',
                    render: (_, row) => <RowName row={row} showProject={showProject}/>,
                },
                {
                    key: 'build',
                    title: 'Latest build',
                    onCell: row => emptyRowCell(row, 2),
                    render: (_, row) => row.type === 'BUILD' ? <LatestBuild build={row.build}/> :
                        <EmptyRowMessage row={row}/>,
                },
                {
                    key: 'promotion',
                    title: 'Highest promotion',
                    onCell: row => emptyRowCell(row, 0),
                    render: (_, row) => row.highestRun ?
                        <Space>
                            <PromotionRun promotionRun={row.highestRun} displayPromotionLevelName={true}/>
                            <Timestamp value={row.highestRun.creation.time}/>
                        </Space> :
                        <NotPromotedYet/>,
                },
            ]}
        />
    )
}

/**
 * What the popover of a step says: when the latest build was granted the level, or that it was not.
 */
function LadderStepPopover({step}) {
    const {promotionLevel, run, granted} = step
    return (
        <Flex vertical gap={2} data-testid={`highest-promotions-step-popover-${promotionLevel.id}`}>
            <Typography.Text strong>{promotionLevel.name}</Typography.Text>
            {
                granted ?
                    <>
                        <Timestamp value={run.creation.time} prefix="promoted "/>
                        {
                            promotionLevel.description &&
                            <Typography.Text type="secondary">{promotionLevel.description}</Typography.Text>
                        }
                    </> :
                    <Typography.Text type="secondary">not granted to this build</Typography.Text>
            }
        </Flex>
    )
}

function LadderStep({step}) {
    const {token} = theme.useToken()
    const {promotionLevel, run, granted, highest} = step
    const icon = <PromotionLevelImage promotionLevel={promotionLevel} size={24} disabled={!granted}/>
    return (
        // On focus as well as on hover, for the keyboard: a granted step is reached through its link,
        // a step not granted through itself
        <Popover content={<LadderStepPopover step={step}/>} trigger={['hover', 'focus']}>
            <Flex
                vertical
                align="center"
                gap={4}
                style={{minWidth: 64}}
                tabIndex={granted ? undefined : 0}
                data-testid={`highest-promotions-step-${promotionLevel.id}`}
                data-granted={String(granted)}
                data-highest={String(highest)}
            >
                <span style={{
                    display: 'inline-flex',
                    padding: 3,
                    borderRadius: '50%',
                    boxShadow: highest ? `0 0 0 2px ${token.colorPrimary}` : undefined,
                }}>
                    {granted ? <Link href={promotionRunUri(run)}>{icon}</Link> : icon}
                </span>
                <Typography.Text strong={highest} type={granted ? undefined : 'secondary'} style={{fontSize: '85%'}}>
                    {promotionLevel.name}
                </Typography.Text>
            </Flex>
        </Popover>
    )
}

function HighestPromotionsLadder({rows, showProject}) {
    const {token} = theme.useToken()
    return (
        <Flex vertical>
            {
                rows.map(row => (
                    <Flex
                        key={row.key}
                        data-testid={`highest-promotions-row-${row.key}`}
                        wrap
                        gap={16}
                        justify="space-between"
                        align="center"
                        style={{padding: '4px 0', borderBottom: `1px solid ${token.colorBorderSecondary}`}}
                    >
                        <Flex vertical gap={2} style={{minWidth: 240}}>
                            <RowName row={row} showProject={showProject}/>
                            {
                                row.type === 'BUILD' ?
                                    <>
                                        <LatestBuild build={row.build}/>
                                        {/* The ring of the ladder marks the highest promotion, if any */}
                                        {!row.highestRun && <NotPromotedYet/>}
                                    </> :
                                    <EmptyRowMessage row={row}/>
                            }
                        </Flex>
                        {
                            row.type === 'BUILD' &&
                            <Flex wrap gap={8}>
                                {
                                    promotionLadder(row.branch.promotionLevels, row.build.promotionRuns).map(step =>
                                        <LadderStep key={step.promotionLevel.id} step={step}/>
                                    )
                                }
                            </Flex>
                        }
                    </Flex>
                ))
            }
        </Flex>
    )
}

/**
 * The latest build of each branch of a list, and the highest promotion it reached (#2006).
 *
 * @param title Overrides the default title
 * @param format `TABLE` or `LADDER`, shown until the viewer switches it in the widget
 * @param showProject Whether the branches are shown with the name of their project
 * @param sort `LAST_ACTIVITY` or `NAME`, how the branches are sorted (#2110)
 * @param branches Entries naming a branch, or a pattern on the names of the branches of a project
 */
export default function HighestPromotionsPerBranchWidget({
                                                             title,
                                                             format = DEFAULT_HIGHEST_PROMOTIONS_FORMAT,
                                                             showProject = DEFAULT_HIGHEST_PROMOTIONS_SHOW_PROJECT,
                                                             sort = DEFAULT_HIGHEST_PROMOTIONS_SORT,
                                                             branches = [],
                                                         }) {

    const {uuid, setTitle, setExtra} = useContext(DashboardWidgetCellContext)

    // The format the viewer switched to, in this browser only; the configured one otherwise
    const [pickedFormat, setPickedFormat] = useState(() => uuid ? getLocalHighestPromotionsFormat(uuid) : undefined)
    const actualFormat = pickedFormat ?? format

    const onFormatChange = value => {
        if (uuid) {
            setLocalHighestPromotionsFormat(uuid, value)
        }
        setPickedFormat(value)
    }

    useEffect(() => {
        setTitle(title || DEFAULT_TITLE)
    }, [title])

    useEffect(() => {
        setExtra(
            <Segmented
                data-testid="highest-promotions-format"
                size="small"
                value={actualFormat}
                options={HIGHEST_PROMOTIONS_FORMATS}
                onChange={onFormatChange}
            />
        )
    }, [actualFormat, uuid])

    const entryQueries = branches.map(entryQuery)
    const queries = entryQueries.filter(Boolean)
    const {data: results, loading, error} = useQueries(queries, {
        deps: [JSON.stringify(branches)],
    })

    // Back to one list of branches per entry, an entry with no query resolving to none
    let next = 0
    const branchesPerEntry = entryQueries.map(entryQuery => {
        if (!entryQuery) return []
        const data = results?.[next++]
        return data ? entryQuery.branches(data) : []
    })
    const loaded = queries.length === 0 || results?.length === queries.length
    const rows = loaded ? sortHighestPromotionsRows(highestPromotionsRows(branches, branchesPerEntry), sort) : []

    return (
        <div data-testid="highest-promotions-per-branch" data-format={actualFormat}>
            {
                error && <Alert type="error" showIcon message={error}/>
            }
            {/* `loading` is false until the first fetch starts: not "No data" before anything is loaded */}
            <LoadingContainer loading={loading || (!loaded && !error)}>
                {
                    branches.length === 0 ?
                        <Empty description="No branch is configured for this widget."/> :
                        (
                            actualFormat === 'LADDER' ?
                                <HighestPromotionsLadder rows={rows} showProject={showProject}/> :
                                <HighestPromotionsTable rows={rows} showProject={showProject}/>
                        )
                }
            </LoadingContainer>
        </div>
    )
}
