import {useState} from "react";
import {gql} from "graphql-request";
import Link from "next/link";
import {Alert, Empty, Skeleton, Space, Switch, Tabs, Tooltip, Typography} from "antd";
import {FaCheck, FaQuestionCircle, FaTimes} from "react-icons/fa";
import Table from "@components/common/table/Table";
import {useQuery} from "@components/services/GraphQL";
import {projectScorecardUri} from "@components/common/Links";
import {gqlLabelFragment} from "@components/labels/LabelGraphQLFragments";
import TimestampText from "@components/common/TimestampText";
import SetExplanation from "@components/extension/scorecard/SetExplanation";
import SecondaryText from "@components/extension/scorecard/SecondaryText";
import EstateFindingsFanOut from "@components/extension/scorecard/estates/EstateFindingsFanOut";
import ScorecardInfo from "@components/extension/scorecard/ScorecardInfo";
import ReadingDefinition from "@components/extension/scorecard/ReadingDefinition";
import RungHelp from "@components/extension/scorecard/RungHelp";
import {
    formatReadingValue,
    isNeutralJudgement,
    latestComputedAt,
    neutralLabel,
    readingJudgement,
    readingName,
    targetText,
    targetWords,
    unknownReasonText,
} from "@components/extension/scorecard/scorecardModel";
import {
    ESTATE_ROLLUP_TEXT,
    estateReadingKeys,
    ESTATE_TAB_FANOUT,
    ESTATE_TAB_READINGS,
    estateRows,
    formatMedian,
    hasEstimatedReading,
    PROJECT_SORT_KEY,
    rollUp,
    sortEstateRows,
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

const cellStyle = (background, color) => ({
    display: 'inline-flex',
    alignItems: 'center',
    gap: 6,
    padding: '2px 8px',
    borderRadius: 6,
    background,
    color,
    fontWeight: 600,
    whiteSpace: 'nowrap',
})

/**
 * One reading of one project: its value, coloured by its target and judged by an icon too, so that
 * the judgement never rests on colour alone. An unknown reading is grey, with its reason on hover
 * and on focus; a neutral reading - a time to restore with no failure in the window, overdue
 * findings with no target set - is said in words, not unknown, with its reason on hover and on focus.
 */
function EstateReadingCell({reading, coverage, testId}) {
    if (!reading) {
        return (
            <span data-testid={testId} data-judgement="NONE">
                <SecondaryText aria-label="Not computed yet" title="Not computed yet">—</SecondaryText>
            </span>
        )
    }
    const judgement = readingJudgement(reading)
    const value = <RungHelp readingKey={reading.key} value={reading.value} coverage={coverage}>
        {formatReadingValue(reading.key, reading.value)}
    </RungHelp>
    let content
    switch (judgement) {
        case 'MET':
            content = <span style={cellStyle('var(--ot-scorecard-met-bg)', 'var(--ot-scorecard-met-text)')}>
                <FaCheck role="img" aria-label="Met"/>
                {value}
            </span>
            break
        case 'MISSED':
            content = <span style={cellStyle('var(--ot-scorecard-missed-bg)', 'var(--ot-scorecard-missed-text)')}>
                <FaTimes role="img" aria-label="Missed"/>
                {value}
            </span>
            break
        case 'UNKNOWN': {
            const reason = unknownReasonText(reading.unknownReason, reading.key)
            content = <Tooltip title={reason}>
                <span
                    tabIndex={0}
                    aria-label={`Unknown: ${reason}`}
                    style={{...cellStyle('var(--ot-scorecard-neutral-bg)', 'var(--ot-text)'), fontWeight: 400}}
                >
                    <FaQuestionCircle aria-hidden="true"/>
                    Unknown
                </span>
            </Tooltip>
            break
        }
        default:
            content = isNeutralJudgement(judgement) ?
                <Tooltip title={unknownReasonText(reading.unknownReason, reading.key)}>
                    <SecondaryText tabIndex={0} style={{whiteSpace: 'nowrap'}}>{neutralLabel(judgement)}</SecondaryText>
                </Tooltip> :
                <span style={{whiteSpace: 'nowrap'}}>{value}</span>
    }
    return <span data-testid={testId} data-judgement={judgement}>{content}</span>
}

/**
 * The roll-up of one reading over the projects of the estate.
 */
function EstateRollUp({readingKey, rows, coverage}) {
    const {median, unknown, missed} = rollUp(rows, readingKey)
    return (
        <Space orientation="vertical" size={0} data-testid={`estate-rollup-${readingKey}`}>
            <Typography.Text strong style={{whiteSpace: 'nowrap'}}>
                Median <RungHelp readingKey={readingKey} value={median} coverage={coverage}>
                    {formatMedian(readingKey, median)}
                </RungHelp>
            </Typography.Text>
            <SecondaryText style={{fontSize: 12, whiteSpace: 'nowrap'}}>{unknown} unknown</SecondaryText>
            <SecondaryText style={{fontSize: 12, whiteSpace: 'nowrap'}}>{missed} missed</SecondaryText>
        </Space>
    )
}

/**
 * The readings of the projects of an estate: one row per project, one column per reading, coloured
 * by the targets of the estate, with a roll-up row and a sort on every column. Each project links to
 * its scorecard page, on the set of the estate.
 */
function EstateReadingsTable({estate}) {

    const [sort, setSort] = useState({key: PROJECT_SORT_KEY, order: 'ascend'})
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
                title: <Space orientation="vertical" size={0} data-testid={`estate-column-${key}`}>
                    <span style={{display: 'inline-flex', alignItems: 'center', whiteSpace: 'nowrap'}}>
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
                        reading={row.readings[key]}
                        coverage={coverage}
                        testId={`estate-cell-${row.project.name}-${key}`}
                    />,
            }
        }),
    ]

    const onChange = (_pagination, _filters, sorter) => {
        const {columnKey, order} = Array.isArray(sorter) ? sorter[0] : sorter
        setSort(order ? {key: columnKey, order} : {key: PROJECT_SORT_KEY, order: 'ascend'})
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
                        </Table.Summary.Row>
                    </Table.Summary>
                }
            />
            <SecondaryText style={{fontSize: 12}} data-testid="estate-legend">
                {latest ? <>Computed <TimestampText value={latest} relative={true}/> · </> : 'Not computed yet · '}
                latest daily reading of each project in this estate
            </SecondaryText>
        </Space>
    )
}

/**
 * The scorecard of an estate: what the estate is, the readings of its projects in a tab, and the
 * fan-out of one finding over its projects in another.
 *
 * Its tab and the finding searched are kept by the page, in its URL, so that a fan-out can be
 * shared by its link — see `estatePageState`.
 *
 * @param name Name of the estate
 * @param tab Tab shown, the readings by default
 * @param finding External ID of the finding whose fan-out is shown, `null` for none
 * @param onChange Called with the new `{tab, finding}` when the user changes the one or the other
 */
export default function EstateScorecardView({name, tab = ESTATE_TAB_READINGS, finding = null, onChange = () => {}}) {

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
                onChange={key => onChange({tab: key, finding})}
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
                            externalId={finding}
                            onExternalIdChange={externalId => onChange({tab: ESTATE_TAB_FANOUT, finding: externalId})}
                        />,
                    },
                ]}
            />
        </Space>
    )
}
