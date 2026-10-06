import {useState} from "react";
import {gql} from "graphql-request";
import Link from "next/link";
import {Alert, Empty, Skeleton, Space, Switch, Tabs, Tooltip, Typography} from "antd";
import {FaCheck, FaQuestionCircle, FaTimes} from "react-icons/fa";
import Table from "@components/common/table/Table";
import {useQuery} from "@components/services/GraphQL";
import {projectScorecardUri} from "@components/common/Links";
import {gqlLabelFragment} from "@components/labels/LabelGraphQLFragments";
import SetExplanation from "@components/extension/scorecard/SetExplanation";
import SecondaryText from "@components/extension/scorecard/SecondaryText";
import EstateFindingsFanOut from "@components/extension/scorecard/estates/EstateFindingsFanOut";
import ScorecardInfo from "@components/extension/scorecard/ScorecardInfo";
import ReadingDefinition from "@components/extension/scorecard/ReadingDefinition";
import RungHelp from "@components/extension/scorecard/RungHelp";
import {ScorecardLegendItem, ScorecardLegendLine} from "@components/extension/scorecard/ScorecardLegend";
import {
    formatReadingValue,
    isNeutralJudgement,
    latestComputedAt,
    neutralLabel,
    readingJudgement,
    readingName,
    rungDescription,
    rungOf,
    targetText,
    targetWords,
    unknownReasonText,
} from "@components/extension/scorecard/scorecardModel";
import {
    countSegments,
    countsLabel,
    countsText,
    countWords,
    DEFAULT_ESTATE_SORT,
    ESTATE_ROLLUP_TEXT,
    estateJudgement,
    estateReadingKeys,
    estateRowCounts,
    ESTATE_TAB_FANOUT,
    ESTATE_TAB_READINGS,
    estateRows,
    formatMedian,
    hasEstimatedReading,
    metText,
    PROJECT_SORT_KEY,
    rollUp,
    sortEstateRows,
    SUMMARY_SORT_KEY,
} from "@components/extension/scorecard/estates/estateViewModel";

export const gqlEstateScorecard = gql`
    query EstateScorecard($name: String!) {
        estate(name: $name) {
            id
            name
            description
            labels {
                ...labelFragment
            }
            marker {
                kind
                levelName
                environment
                qualifier
            }
            security {
                expectedKinds
                freshnessDays
            }
            readingConfigs {
                key
                windowDays
                target
                direction
            }
            projectSets {
                project {
                    id
                    name
                }
                readings {
                    key
                    computedAt
                    value
                    basis
                    unknownReason
                    direction
                    target
                    targetMet
                }
            }
        }
    }
    ${gqlLabelFragment}
`

/**
 * Width of a cell of the heatmap: about ten readings, the project and the summary fit a window
 * 1280 px wide. A value longer than this is cut, and given in full on hover.
 */
const CELL_WIDTH = 80
const CELL_HEIGHT = 28

/**
 * Horizontal padding of a cell of a small table.
 */
const TABLE_CELL_PADDING = 16

/**
 * Width of the column of a reading, with the padding of a small table.
 */
const READING_COLUMN_WIDTH = CELL_WIDTH + TABLE_CELL_PADDING

/**
 * Width of the summary of a project, and of its column.
 */
const SUMMARY_WIDTH = 96

const blockStyle = {
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    boxSizing: 'border-box',
    width: CELL_WIDTH,
    height: CELL_HEIGHT,
    borderRadius: 4,
    fontSize: 12,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
}

/**
 * The fills of a cell, by its judgement - see `estateJudgement`. An unknown reading has none, only
 * a dashed outline, so that it never looks judged.
 */
const CELL_FILLS = {
    met: {
        backgroundColor: 'var(--ot-scorecard-met-bg)',
        color: 'var(--ot-scorecard-met-text)',
        fontWeight: 600,
    },
    missed: {
        backgroundColor: 'var(--ot-scorecard-missed-bg)',
        color: 'var(--ot-scorecard-missed-text)',
        fontWeight: 600,
    },
    neutral: {
        backgroundColor: 'var(--ot-scorecard-neutral-bg)',
        color: 'var(--ot-text)',
    },
    unknown: {
        backgroundColor: 'transparent',
        borderWidth: 1,
        borderStyle: 'dashed',
        borderColor: 'var(--ot-scorecard-unknown)',
        color: 'var(--ot-text)',
    },
}

/**
 * The segments of a stacked bar by judgement — see `countSegments`.
 */
const SEGMENT_STYLES = {
    missed: {backgroundColor: 'var(--ot-scorecard-missed)'},
    met: {backgroundColor: 'var(--ot-scorecard-met)'},
    neutral: {backgroundColor: 'var(--ot-scorecard-neutral)'},
    unknown: {
        backgroundColor: 'transparent',
        borderWidth: 1,
        borderStyle: 'dashed',
        borderColor: 'var(--ot-scorecard-unknown)',
    },
}

/**
 * A thin bar of counts by judgement, the missed ones first, which spells the counts out in its label.
 */
function CountsBar({counts, testId}) {
    const segments = countSegments(counts)
    return (
        <div
            role="img"
            aria-label={countsLabel(counts)}
            data-testid={testId}
            style={{
                display: 'flex',
                gap: 1,
                width: '100%',
                height: 8,
                borderRadius: 2,
                overflow: 'hidden',
                backgroundColor: segments.length === 0 ? 'var(--ot-border-subtle)' : undefined,
            }}
        >
            {
                segments.map(({judgement, count}) =>
                    <span
                        key={judgement}
                        data-judgement={judgement}
                        style={{flex: `${count} 0 0`, boxSizing: 'border-box', ...SEGMENT_STYLES[judgement]}}
                    />
                )
            }
        </div>
    )
}

/**
 * What the hover of a cell says: the project, the reading, its full value and its target, and why
 * it is unknown or neutral, or what its rung means.
 */
function EstateCellDetails({project, reading, valueText, note, testId}) {
    const target = targetText(reading)
    return (
        <div data-testid={testId}>
            <div style={{fontWeight: 600}}>{project.name}</div>
            <div>{readingName(reading.key)}: {valueText}</div>
            <div>{target ? `Target: ${target}` : 'No target set by this estate'}</div>
            {note && <div>{note}</div>}
        </div>
    )
}

const CellValue = ({children}) =>
    <span style={{overflow: 'hidden', textOverflow: 'ellipsis'}}>{children}</span>

const judgedContent = (icon, formatted) => <>
    {icon}
    <CellValue>{formatted}</CellValue>
</>

/**
 * What the cell of a reading shows and says, by its judgement: its `content`, its value in words
 * for its hover, `valueText`, a `note` for its hover - why it is unknown or neutral, what its rung
 * means - and the `label` of a cell whose content does not say it all - unknown, a rung.
 */
const cellParts = (reading, coverage) => {
    const judgement = readingJudgement(reading)
    const formatted = formatReadingValue(reading.key, reading.value)
    const rung = rungOf(reading.key, reading.value)
    const rungText = rung !== null ? rungDescription(rung, coverage) : null
    const rungLabel = rungText ? `${formatted}: ${rungText}` : undefined
    const reason = unknownReasonText(reading.unknownReason, reading.key)
    switch (judgement) {
        case 'MET':
            return {
                content: judgedContent(<FaCheck role="img" aria-label="Met" style={{flexShrink: 0}}/>, formatted),
                valueText: `${formatted} — met`,
                note: rungText,
                label: rungLabel,
            }
        case 'MISSED':
            return {
                content: judgedContent(<FaTimes role="img" aria-label="Missed" style={{flexShrink: 0}}/>, formatted),
                valueText: `${formatted} — missed`,
                note: rungText,
                label: rungLabel,
            }
        case 'UNKNOWN':
            return {
                content: <><FaQuestionCircle aria-hidden="true" style={{flexShrink: 0}}/>Unknown</>,
                valueText: 'Unknown',
                note: reason,
                label: `Unknown: ${reason}`,
            }
        default:
            return isNeutralJudgement(judgement) ? {
                content: <CellValue>{neutralLabel(judgement)}</CellValue>,
                valueText: neutralLabel(judgement),
                note: reason,
            } : {
                content: <CellValue>{formatted}</CellValue>,
                valueText: formatted,
                note: rungText,
                label: rungLabel,
            }
    }
}

/**
 * One reading of one project: a block of the heatmap, filled with the colour of its judgement and
 * judged by an icon too, so that the judgement never rests on colour alone, with its value. An
 * unknown reading is a dashed outline, with its reason on hover and on focus; a neutral reading -
 * a value with no target, a time to restore with no failure in the window, overdue findings with
 * no target set - is said in words, not unknown, with its reason on hover and on focus. The hover
 * gives the project, the reading, the full value and the target.
 */
function EstateReadingCell({project, reading, coverage, testId}) {
    if (!reading) {
        return (
            <span data-testid={testId} data-judgement="NONE" data-fill="none" style={blockStyle}>
                <SecondaryText aria-label="Not computed yet" title="Not computed yet">—</SecondaryText>
            </span>
        )
    }
    const {content, valueText, note, label} = cellParts(reading, coverage)
    const fill = estateJudgement(reading)
    return (
        <Tooltip
            trigger={['hover', 'focus']}
            title={
                <EstateCellDetails
                    project={project}
                    reading={reading}
                    valueText={valueText}
                    note={note}
                    testId={`estate-cell-details-${project.name}-${reading.key}`}
                />
            }
        >
            <span
                data-testid={testId}
                data-judgement={readingJudgement(reading)}
                data-fill={fill}
                style={{...blockStyle, ...CELL_FILLS[fill]}}
            >
                {/* Focusable, so that the keyboard gets the details of its hover too: a value cut short, a reason */}
                <span
                    tabIndex={0}
                    aria-label={label}
                    style={{
                        display: 'inline-flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        gap: 4,
                        width: '100%',
                        height: '100%',
                        padding: '0 6px',
                        overflow: 'hidden',
                        cursor: 'help',
                    }}
                >
                    {content}
                </span>
            </span>
        </Tooltip>
    )
}

/**
 * The summary of one project: a bar of its readings by judgement, and how many of its judged
 * readings are met.
 */
function EstateProjectSummary({row}) {
    const counts = estateRowCounts(row)
    return (
        <div
            data-testid={`estate-summary-${row.project.name}`}
            style={{display: 'flex', flexDirection: 'column', gap: 4, width: SUMMARY_WIDTH}}
        >
            <CountsBar counts={counts} testId={`estate-summary-bar-${row.project.name}`}/>
            <SecondaryText style={{fontSize: 12, whiteSpace: 'nowrap'}}>{metText(counts)}</SecondaryText>
        </div>
    )
}

/**
 * The mark of a judgement in a line of counts: the icon of its cell, a swatch of its bar for the
 * neutral ones, which have no icon.
 */
const COUNT_MARKS = {
    missed: <FaTimes aria-hidden="true" size={9} style={{color: 'var(--ot-scorecard-missed-text)'}}/>,
    met: <FaCheck aria-hidden="true" size={9} style={{color: 'var(--ot-scorecard-met-text)'}}/>,
    neutral: <span
        aria-hidden="true"
        style={{display: 'inline-block', width: 8, height: 8, borderRadius: 2, ...SEGMENT_STYLES.neutral}}
    />,
    unknown: <FaQuestionCircle aria-hidden="true" size={9}/>,
}

/**
 * Counts by judgement in one short line, the zeros left out: each one a mark and a number, said in
 * words in its label and in the hover of the line. Four counts of one digit fit in a cell.
 */
function CountsLine({counts, testId}) {
    const segments = countSegments(counts)
    return (
        <SecondaryText
            data-testid={testId}
            title={countsText(counts)}
            style={{fontSize: 11, display: 'inline-flex', alignItems: 'center', columnGap: 3, whiteSpace: 'nowrap'}}
        >
            {segments.length === 0 && countsText(counts)}
            {
                segments.map(({judgement, count}) =>
                    <span
                        key={judgement}
                        role="img"
                        aria-label={countWords(judgement, count)}
                        style={{display: 'inline-flex', alignItems: 'center', gap: 2}}
                    >
                        {COUNT_MARKS[judgement]}{count}
                    </span>
                )
            }
        </SecondaryText>
    )
}

/**
 * The roll-up of one reading over the projects of the estate: its median, above a bar of the
 * projects by judgement, and their counts.
 */
function EstateRollUp({readingKey, rows, coverage}) {
    const counts = rollUp(rows, readingKey)
    return (
        <div
            data-testid={`estate-rollup-${readingKey}`}
            style={{display: 'flex', flexDirection: 'column', gap: 4, width: CELL_WIDTH}}
        >
            <Typography.Text strong style={{fontSize: 12}}>
                Median <RungHelp readingKey={readingKey} value={counts.median} coverage={coverage}>
                    {formatMedian(readingKey, counts.median)}
                </RungHelp>
            </Typography.Text>
            <CountsBar counts={counts} testId={`estate-rollup-bar-${readingKey}`}/>
            <CountsLine counts={counts} testId={`estate-rollup-counts-${readingKey}`}/>
        </div>
    )
}

/**
 * The swatch of a judgement in the legend: a small cell, with its icon.
 */
const JudgementSwatch = ({fill, icon}) =>
    <span
        aria-hidden="true"
        style={{
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            boxSizing: 'border-box',
            width: 20,
            height: 14,
            borderRadius: 3,
            fontSize: 10,
            ...fill,
        }}
    >
        {icon}
    </span>

/**
 * The line under the readings: when they were computed, and a legend of the judgements of a cell.
 */
function EstateLegend({latest}) {
    return (
        <ScorecardLegendLine
            testId="estate-legend"
            latest={latest}
            notComputed="Not computed yet"
            description="latest daily reading of each project in this estate"
        >
            <ScorecardLegendItem testId="estate-legend-met" swatch={<JudgementSwatch fill={CELL_FILLS.met} icon={<FaCheck/>}/>}>
                Met
            </ScorecardLegendItem>
            <ScorecardLegendItem testId="estate-legend-missed" swatch={<JudgementSwatch fill={CELL_FILLS.missed} icon={<FaTimes/>}/>}>
                Missed
            </ScorecardLegendItem>
            <ScorecardLegendItem testId="estate-legend-neutral" swatch={<JudgementSwatch fill={CELL_FILLS.neutral}/>}>
                Neutral: not judged
            </ScorecardLegendItem>
            <ScorecardLegendItem testId="estate-legend-unknown" swatch={<JudgementSwatch fill={CELL_FILLS.unknown} icon={<FaQuestionCircle/>}/>}>
                Unknown
            </ScorecardLegendItem>
            <ScorecardLegendItem testId="estate-legend-none" swatch={<JudgementSwatch icon="—"/>}>
                Not computed yet
            </ScorecardLegendItem>
        </ScorecardLegendLine>
    )
}

/**
 * The readings of the projects of an estate as a heatmap: one row per project, one column per
 * reading, each cell filled by its judgement against the targets of the estate, a summary of each
 * project on the right, a roll-up row and a sort on every column - the worst projects first by
 * default. Each project links to its scorecard page, on the set of the estate.
 */
function EstateReadingsTable({estate}) {

    const [sort, setSort] = useState(DEFAULT_ESTATE_SORT)
    const [measuredOnly, setMeasuredOnly] = useState(false)

    const projectSets = estate.projectSets ?? []
    const keys = estateReadingKeys(projectSets)
    const estimated = hasEstimatedReading(projectSets)
    const rows = estateRows(projectSets, {measuredOnly: estimated && measuredOnly})
    const sortedRows = sortEstateRows(rows, sort)
    const targets = Object.fromEntries((estate.readingConfigs ?? []).map(config => [config.key, config]))
    const coverage = estate.security ?? null
    const latest = latestComputedAt({sets: projectSets})

    const sortOrder = (key) => sort.key === key ? sort.order : null

    const columns = [
        {
            key: PROJECT_SORT_KEY,
            title: <span data-testid="estate-column-project">Project</span>,
            fixed: 'left',
            sorter: true,
            sortOrder: sortOrder(PROJECT_SORT_KEY),
            render: (_, row) =>
                <Link
                    href={projectScorecardUri(row.project, estate.name)}
                    data-testid={`estate-project-${row.project.name}`}
                >
                    {row.project.name}
                </Link>,
        },
        ...keys.map(key => {
            const target = targets[key] ? targetText({key, ...targets[key]}) : null
            const name = readingName(key)
            return {
                key,
                width: READING_COLUMN_WIDTH,
                title: <Space orientation="vertical" size={0} data-testid={`estate-column-${key}`}>
                    <span style={{display: 'inline-flex', alignItems: 'center', fontSize: 12}}>
                        {name}
                        <ScorecardInfo
                            label={`About ${name}`}
                            title={name}
                            testId={`estate-column-info-${key}`}
                            content={
                                <Space orientation="vertical" size={8}>
                                    <ReadingDefinition
                                        readingKey={key}
                                        markerKind={estate.marker?.kind}
                                        coverage={coverage}
                                        target={targets[key]?.target ?? null}
                                    />
                                    <Typography.Text strong>
                                        {targetWords({key, ...targets[key]})}
                                    </Typography.Text>
                                </Space>
                            }
                        />
                    </span>
                    {
                        target &&
                        <SecondaryText style={{fontSize: 12, fontWeight: 400, whiteSpace: 'nowrap'}}>{target}</SecondaryText>
                    }
                </Space>,
                sorter: true,
                sortOrder: sortOrder(key),
                render: (_, row) =>
                    <EstateReadingCell
                        project={row.project}
                        reading={row.readings[key]}
                        coverage={coverage}
                        testId={`estate-cell-${row.project.name}-${key}`}
                    />,
            }
        }),
        {
            key: SUMMARY_SORT_KEY,
            title: <span data-testid="estate-column-summary" style={{fontSize: 12}}>Summary</span>,
            width: SUMMARY_WIDTH + TABLE_CELL_PADDING,
            sorter: true,
            // The worst projects first, then the other way
            sortDirections: ['descend', 'ascend'],
            sortOrder: sortOrder(SUMMARY_SORT_KEY),
            render: (_, row) => <EstateProjectSummary row={row}/>,
        },
    ]

    const onChange = (_pagination, _filters, sorter) => {
        const {columnKey, order} = Array.isArray(sorter) ? sorter[0] : sorter
        setSort(order ? {key: columnKey, order} : DEFAULT_ESTATE_SORT)
    }

    return (
        <Space orientation="vertical" size={16} style={{width: '100%'}}>
            {
                estimated &&
                <Space size={8}>
                    <Switch
                        data-testid="estate-measured-only"
                        checked={measuredOnly}
                        onChange={setMeasuredOnly}
                        aria-label="Measured readings only"
                    />
                    <Typography.Text>Measured readings only</Typography.Text>
                </Space>
            }
            <Table
                data-testid="estate-readings"
                dataSource={sortedRows}
                columns={columns}
                rowKey={row => row.project.id}
                pagination={false}
                size="small"
                scroll={{x: 'max-content'}}
                onChange={onChange}
                showSorterTooltip={false}
                locale={{emptyText: 'No project in this estate'}}
                summary={() =>
                    rows.length > 0 &&
                    <Table.Summary fixed="top">
                        <Table.Summary.Row data-testid="estate-rollup">
                            <Table.Summary.Cell index={0}>
                                <span style={{display: 'inline-flex', alignItems: 'center', whiteSpace: 'nowrap'}}>
                                    <Typography.Text strong>All projects</Typography.Text>
                                    <ScorecardInfo
                                        label="About All projects"
                                        title="All projects"
                                        testId="estate-rollup-info"
                                        content={<Typography.Text>{ESTATE_ROLLUP_TEXT}</Typography.Text>}
                                    />
                                </span>
                            </Table.Summary.Cell>
                            {
                                keys.map((key, index) =>
                                    <Table.Summary.Cell key={key} index={index + 1}>
                                        <EstateRollUp readingKey={key} rows={rows} coverage={coverage}/>
                                    </Table.Summary.Cell>
                                )
                            }
                            <Table.Summary.Cell index={keys.length + 1}/>
                        </Table.Summary.Row>
                    </Table.Summary>
                }
            />
            <EstateLegend latest={latest}/>
        </Space>
    )
}

/**
 * The scorecard of an estate: what the estate is, the readings of its projects in a tab, and the
 * fan-out of one finding over its projects in another.
 *
 * Its tab, the finding shown and the text searched are kept by the page, in its URL, so that a
 * fan-out or a search can be shared by its link — see `estatePageState`.
 *
 * @param name Name of the estate
 * @param tab Tab shown, the readings by default
 * @param finding External ID of the finding whose fan-out is shown, `null` for none
 * @param search Text searched among the external IDs of the findings, `null` for none
 * @param onChange Called with the new `{tab, finding, search}` when the user changes any of them
 */
export default function EstateScorecardView({
                                                name,
                                                tab = ESTATE_TAB_READINGS,
                                                finding = null,
                                                search = null,
                                                onChange = () => {},
                                            }) {

    const {data: estate, finished, error} = useQuery(
        gqlEstateScorecard,
        {
            variables: {name},
            deps: [name],
            condition: !!name,
            dataFn: data => data.estate,
        }
    )

    if (!finished) {
        return <Skeleton active/>
    }
    if (error) {
        return <Alert type="error" showIcon title={error}/>
    }
    if (!estate) {
        return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={`No estate is named "${name}".`}/>
    }

    return (
        <Space orientation="vertical" size={16} style={{width: '100%'}} data-testid="estate-scorecard">
            <SetExplanation set={{estate}} testId="estate-explanation"/>
            <Tabs
                activeKey={tab}
                onChange={key => onChange({tab: key, finding, search})}
                items={[
                    {
                        key: ESTATE_TAB_READINGS,
                        label: 'Readings',
                        children: <EstateReadingsTable estate={estate}/>,
                    },
                    {
                        key: ESTATE_TAB_FANOUT,
                        label: 'Findings fan-out',
                        children: <EstateFindingsFanOut
                            estate={estate}
                            finding={finding}
                            search={search}
                            onChange={state => onChange({tab: ESTATE_TAB_FANOUT, ...state})}
                        />,
                    },
                ]}
            />
        </Space>
    )
}
