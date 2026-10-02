import {Descriptions, Space, Tag, Tooltip, Typography} from "antd";
import SecondaryText from "@components/extension/scorecard/SecondaryText";
import {FaCheck, FaQuestionCircle, FaTimes} from "react-icons/fa";
import TimestampText from "@components/common/TimestampText";
import ScorecardInfo from "@components/extension/scorecard/ScorecardInfo";
import ReadingSparkline from "@components/extension/scorecard/ReadingSparkline";
import ReadingDefinition from "@components/extension/scorecard/ReadingDefinition";
import RungHelp from "@components/extension/scorecard/RungHelp";
import {
    formatReadingValue,
    isNeutralJudgement,
    markerText,
    neutralText,
    readingCoverage,
    readingDescription,
    readingDetailItems,
    readingJudgement,
    readingName,
    readingUsesMarker,
    scopeText,
    targetLineText,
    targetText,
    unknownReasonText,
    windowDays,
} from "@components/extension/scorecard/scorecardModel";

const timestampFormat = "YYYY MMM DD, HH:mm"

const tagStyle = (background, color) => ({
    background,
    color,
    borderRadius: 999,
    fontWeight: 600,
    marginInlineEnd: 0,
    display: 'inline-flex',
    alignItems: 'center',
    gap: 4,
})

const NEUTRAL_TAG = tagStyle('var(--ot-scorecard-neutral-bg)', 'var(--ot-text)')

/**
 * Suffix of the test ID of a neutral judgement: `no-failure`, `no-target`.
 */
const neutralTestIdSuffix = (judgement) => judgement.toLowerCase().replaceAll('_', '-')

/**
 * The judgement of a reading as a tag, always in words - `Met` and `Missed` with an icon too, so
 * that they never rest on colour alone. An unknown reading gives its reason on hover and on focus; a
 * neutral one - no failure in the window, no target set - says so in words, with no unknown chip.
 */
export function JudgementTag({reading, testId}) {
    const judgement = readingJudgement(reading)
    const sub = (suffix) => testId ? `${testId}-${suffix}` : undefined
    const props = {'data-testid': sub('judgement'), 'data-judgement': judgement, variant: 'filled'}
    switch (judgement) {
        case 'MET':
            return <Tag {...props} icon={<FaCheck aria-hidden="true"/>}
                        style={tagStyle('var(--ot-scorecard-met-bg)', 'var(--ot-scorecard-met-text)')}>
                Met
            </Tag>
        case 'MISSED':
            return <Tag {...props} icon={<FaTimes aria-hidden="true"/>}
                        style={tagStyle('var(--ot-scorecard-missed-bg)', 'var(--ot-scorecard-missed-text)')}>
                Missed
            </Tag>
        case 'UNKNOWN':
            return <span {...props}>
                <Tooltip title={unknownReasonText(reading.unknownReason, reading.key)}>
                    <Tag
                        variant="filled"
                        icon={<FaQuestionCircle aria-hidden="true"/>}
                        tabIndex={0}
                        aria-label={`Unknown: ${unknownReasonText(reading.unknownReason, reading.key)}`}
                        data-testid={sub('unknown')}
                        style={NEUTRAL_TAG}
                    >
                        Unknown
                    </Tag>
                </Tooltip>
            </span>
        default:
            if (isNeutralJudgement(judgement)) {
                return <Tag {...props} style={NEUTRAL_TAG}>
                    <span data-testid={sub(neutralTestIdSuffix(judgement))}>
                        {neutralText(judgement)}
                    </span>
                </Tag>
            }
            return <Tag {...props} style={NEUTRAL_TAG}>No target</Tag>
    }
}

/**
 * What explains a reading, as on the former reading card of the scorecard page: the window, the
 * branches which fed it, the marker it was read up to, the target, its details, and when it was
 * computed.
 */
const detailItems = (reading) => {
    const days = windowDays(reading)
    const target = targetText(reading)
    return [
        {
            key: 'window',
            label: 'Window',
            children: <Space size={4} wrap>
                {days !== null && <Typography.Text>{days} days,</Typography.Text>}
                <TimestampText value={reading.windowStart} format={timestampFormat}/>
                <Typography.Text>→</Typography.Text>
                <TimestampText value={reading.windowEnd} format={timestampFormat}/>
            </Space>,
        },
        {
            key: 'branches',
            label: 'Branches',
            children: scopeText(reading.details),
        },
        ...(readingUsesMarker(reading.key) ? [{
            key: 'marker',
            label: 'Marker',
            children: markerText(reading.details),
        }] : []),
        ...(target ? [{
            key: 'target',
            label: 'Target',
            children: target,
        }] : []),
        ...readingDetailItems(reading).map(({key, label, text, timestamp}) => ({
            key,
            label,
            children: timestamp ? <TimestampText value={timestamp} format={timestampFormat}/> : text,
        })),
        {
            key: 'computedAt',
            label: 'Computed',
            children: <TimestampText value={reading.computedAt} format={timestampFormat}/>,
        },
    ]
}

/**
 * One reading of a set, as a tile: its name, its judgement in words, its value, its target, and the
 * trend of its daily snapshots with the zone meeting the target.
 *
 * - `size="default"`, on the project page: the definition of the reading is in a popover.
 * - `size="large"`, on the scorecard page: a larger value, the definition inline, a taller chart
 *   with its axis labelled, and what explains the value under it.
 */
export default function ReadingTile({reading, size = 'default', testId}) {

    const large = size === 'large'
    const name = readingName(reading.key)
    const judgement = readingJudgement(reading)
    const description = readingDescription(reading.key, reading.details?.markerKind)
    const measured = ['MET', 'MISSED', 'SHOWN'].includes(judgement)
    const coverage = readingCoverage(reading)
    const definition = (props) =>
        <ReadingDefinition
            readingKey={reading.key}
            markerKind={reading.details?.markerKind}
            coverage={coverage}
            current={measured ? reading.value : null}
            target={reading.target}
            {...props}
        />
    const sub = (suffix) => testId ? `${testId}-${suffix}` : undefined

    return (
        <div
            data-testid={testId}
            style={{
                height: '100%',
                boxSizing: 'border-box',
                border: '1px solid var(--ot-border-subtle)',
                borderRadius: 12,
                padding: large ? '18px 20px' : '14px 16px',
                background: 'var(--ot-bg-elevated)',
                display: 'flex',
                flexDirection: 'column',
                gap: large ? 10 : 6,
                fontVariantNumeric: 'tabular-nums',
            }}
        >
            <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8, flexWrap: 'wrap'}}>
                <span style={{display: 'inline-flex', alignItems: 'center'}}>
                    {
                        large ?
                            <Typography.Title level={3} style={{margin: 0, fontSize: 15}}>{name}</Typography.Title> :
                            <Typography.Text strong style={{fontSize: 13}}>{name}</Typography.Text>
                    }
                    {
                        !large && description &&
                        <ScorecardInfo
                            label={`About ${name}`}
                            title={name}
                            testId={`scorecard-reading-info-${reading.key}`}
                            content={definition()}
                        />
                    }
                </span>
                <JudgementTag reading={reading} testId={testId}/>
            </div>
            {
                large && description && definition({secondary: true, testId: sub('description')})
            }
            <div style={{display: 'flex', alignItems: 'baseline', gap: large ? 12 : 10, flexWrap: 'wrap'}}>
                <span
                    data-testid={sub('value')}
                    style={{fontSize: large ? 36 : 28, fontWeight: 600, lineHeight: 1.2, letterSpacing: large ? -0.8 : -0.5}}
                >
                    {
                        measured ?
                            <RungHelp readingKey={reading.key} value={reading.value} coverage={coverage}>
                                {formatReadingValue(reading.key, reading.value)}
                            </RungHelp> :
                            '—'
                    }
                </span>
                <SecondaryText style={{fontSize: large ? 13 : 12}} data-testid={sub('target')}>
                    {targetLineText(reading)}
                </SecondaryText>
            </div>
            <ReadingSparkline reading={reading} height={large ? 120 : 40} axisLabels={large}/>
            {
                large &&
                <Descriptions
                    size="small"
                    column={1}
                    items={detailItems(reading)}
                    data-testid={sub('details')}
                    style={{borderTop: '1px solid var(--ot-border-subtle)', paddingTop: 10}}
                />
            }
        </div>
    )
}
