import {useContext, useEffect, useState} from "react";
import {gql} from "graphql-request";
import Link from "next/link";
import {Col, Empty, Row, Skeleton, Space, Tabs, Tag, Typography} from "antd";
import {DashboardWidgetCellContext} from "@components/dashboards/DashboardWidgetCellContextProvider";
import {useQuery} from "@components/services/GraphQL";
import {projectScorecardUri} from "@components/common/Links";
import TimestampText from "@components/common/TimestampText";
import {
    estateMarkerHeadline,
    formatReadingValue,
    hasTargets,
    judgedReadings,
    latestComputedAt,
    orderedSets,
    readingName,
    resolveSet,
    SCORECARD_HISTORY_DAYS,
    setParam,
    setTargetCount,
    sortedReadings,
    targetHeadline,
} from "@components/extension/scorecard/scorecardModel";
import TargetRing from "@components/extension/scorecard/TargetRing";
import ReadingSparkline from "@components/extension/scorecard/ReadingSparkline";
import SecondaryText from "@components/extension/scorecard/SecondaryText";
import {JudgementTag} from "@components/extension/scorecard/ReadingTile";

// The Project view shows six tiles at most, two rows of three
const PROJECT_TILES = 6

export const gqlProjectScorecardWidget = gql`
    query ProjectScorecardWidget($name: String!, $days: Int!) {
        projects(name: $name) {
            id
            name
            scorecard {
                sets {
                    name
                    estate {
                        name
                        marker {
                            kind
                            levelName
                            environment
                            qualifier
                        }
                    }
                    readings {
                        key
                        computedAt
                        value
                        basis
                        unknownReason
                        details
                        direction
                        target
                        targetMet
                        history(days: $days) {
                            day
                            value
                        }
                    }
                }
            }
        }
    }
`

/**
 * An estate: the ring of its targets met, and beside it its judged readings, one line each, met or
 * missed with an icon and a word.
 */
function EstateView({set}) {
    const counted = setTargetCount(set)
    const {headline} = targetHeadline(counted)
    return (
        <div style={{display: 'flex', gap: 20, alignItems: 'center', flexWrap: 'wrap'}} data-testid="scorecard-widget-estate">
            <TargetRing
                met={counted.met}
                count={counted.count}
                size={136}
                thickness={16}
                label={`${headline} in ${set.estate.name}`}
            >
                <span style={{fontSize: 32, fontWeight: 700, lineHeight: 1}}>{counted.met}/{counted.count}</span>
                <SecondaryText style={{fontSize: 11}}>targets met</SecondaryText>
            </TargetRing>
            <ul
                data-testid="scorecard-widget-judged"
                style={{listStyle: 'none', margin: 0, padding: 0, flex: '1 1 180px', minWidth: 0, display: 'flex', flexDirection: 'column', gap: 6}}
            >
                {
                    judgedReadings(set).map(reading =>
                        <li
                            key={reading.key}
                            style={{display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, minHeight: 24}}
                        >
                            <span style={{flexShrink: 0, minWidth: 72}}>
                                <JudgementTag reading={reading} testId={`scorecard-widget-${reading.key}`}/>
                            </span>
                            <span style={{flexGrow: 1, minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap'}}>
                                {readingName(reading.key)}
                            </span>
                            <span style={{fontWeight: 600, whiteSpace: 'nowrap', fontVariantNumeric: 'tabular-nums'}}>
                                {formatReadingValue(reading.key, reading.value)}
                            </span>
                        </li>
                    )
                }
            </ul>
        </div>
    )
}

/**
 * The Project set, or an estate with no judged reading: its readings as small tiles with their
 * trend. The Project set has no targets; the targets of such an estate are drawn, but none of them
 * could be judged.
 */
function ReadingsView({set}) {
    const readings = sortedReadings(set).slice(0, PROJECT_TILES)
    const unjudgedTargets = !!set.estate && hasTargets(set)
    return (
        <Space orientation="vertical" size={10} style={{width: '100%'}} data-testid="scorecard-widget-project">
            <Space size={8}>
                <Tag variant="filled" style={{background: 'var(--ot-scorecard-neutral-bg)', color: 'var(--ot-text)', borderRadius: 999, fontWeight: 600}}>
                    {unjudgedTargets ? 'No target judged' : 'No targets'}
                </Tag>
                <SecondaryText style={{fontSize: 12}}>
                    {unjudgedTargets ? 'Unknown readings' : 'Readings only'} · {SCORECARD_HISTORY_DAYS}-day trend
                </SecondaryText>
            </Space>
            <Row gutter={[10, 10]}>
                {
                    readings.map(reading =>
                        <Col key={reading.key} span={8}>
                            <div
                                data-testid={`scorecard-widget-tile-${reading.key}`}
                                style={{
                                    height: '100%',
                                    boxSizing: 'border-box',
                                    border: '1px solid var(--ot-border-subtle)',
                                    borderRadius: 10,
                                    padding: '8px 10px',
                                    display: 'flex',
                                    flexDirection: 'column',
                                    gap: 2,
                                    fontVariantNumeric: 'tabular-nums',
                                }}
                            >
                                <Typography.Text strong style={{fontSize: 12}} ellipsis>{readingName(reading.key)}</Typography.Text>
                                <span style={{fontSize: 17, fontWeight: 600, whiteSpace: 'nowrap'}}>
                                    {formatReadingValue(reading.key, reading.value)}
                                </span>
                                <ReadingSparkline reading={reading} height={24} target={unjudgedTargets}/>
                            </div>
                        </Col>
                    )
                }
            </Row>
        </Space>
    )
}

/**
 * The "Project scorecard" widget: the targets a project meets in each of its estates, and its
 * readings.
 *
 * A tab per set - "Project", then "<estate> · met/count" per estate - with no tab bar for a project
 * in no estate. The widget opens on the configured set, else on the first estate by name, else on
 * the Project set; a configured estate the project is no longer in falls back on the default, and
 * says so. Switching tabs is local to the page, never saved into the configuration.
 *
 * @param project Name of the project
 * @param set Set to open: `project`, the name of an estate, or nothing for the default
 */
export default function ProjectScorecardWidget({project, set}) {

    const {setTitle} = useContext(DashboardWidgetCellContext)
    useEffect(() => {
        setTitle(project ? `Scorecard · ${project}` : "Project scorecard")
    }, [project])

    // The tab selected on the page, for the configuration it was selected in: a new configuration
    // opens on its own set again
    const configuration = `${project}\n${set ?? ''}`
    const [selection, setSelection] = useState({configuration, tab: null})
    const tab = selection.configuration === configuration ? selection.tab : null

    const {data, finished, error} = useQuery(
        gqlProjectScorecardWidget,
        {
            variables: {name: project, days: SCORECARD_HISTORY_DAYS},
            deps: [project],
            condition: !!project,
            dataFn: data => data.projects?.[0] ?? null,
        }
    )

    if (!project) {
        return <Empty description="Project has not been configured."/>
    }
    if (!finished) {
        return <Skeleton active/>
    }
    if (error) {
        return <Typography.Text type="danger">{error}</Typography.Text>
    }
    if (!data) {
        return <Empty description={`Project ${project} not found.`}/>
    }

    const sets = orderedSets(data.scorecard)
    const configured = resolveSet(sets, set)
    const shown = tab ? resolveSet(sets, tab).set : configured.set
    const hasEstates = sets.some(it => it.estate)
    const latest = latestComputedAt(data.scorecard)

    if (!shown) {
        return <Empty description="The readings of this project have not been computed yet."/>
    }

    const tabLabel = (it) => {
        if (!it.estate) return 'Project'
        const {met, count} = setTargetCount(it)
        return count > 0 ? `${it.estate.name} · ${met}/${count}` : it.estate.name
    }

    const judged = shown.estate && setTargetCount(shown).count > 0

    return (
        <div style={{display: 'flex', flexDirection: 'column', gap: 12, height: '100%', boxSizing: 'border-box', padding: '4px 10px 8px'}}>
            {
                configured.unknown && shown === configured.set &&
                <SecondaryText style={{fontSize: 12}} data-testid="scorecard-widget-fallback">
                    The project is no longer in the estate {set}: showing the default set.
                </SecondaryText>
            }
            {
                hasEstates &&
                <Tabs
                    size="small"
                    activeKey={setParam(shown)}
                    onChange={it => setSelection({configuration, tab: it})}
                    items={sets.map(it => ({key: setParam(it), label: tabLabel(it)}))}
                    tabBarStyle={{marginBottom: 0}}
                />
            }
            {judged ? <EstateView set={shown}/> : <ReadingsView set={shown}/>}
            <div
                data-testid="scorecard-widget-footer"
                style={{marginTop: 'auto', display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8, flexWrap: 'wrap'}}
            >
                <SecondaryText style={{fontSize: 12}}>
                    {shown.estate ? estateMarkerHeadline(shown.estate.marker) : "Up to each branch's last promotion"}
                    {latest && <> · computed <TimestampText value={latest} relative={true}/></>}
                </SecondaryText>
                <Link href={projectScorecardUri(data, setParam(shown))}>Open scorecard</Link>
            </div>
        </div>
    )
}
