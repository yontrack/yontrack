import SecondaryText from "@components/extension/scorecard/SecondaryText";
import {
    Line,
    LineChart,
    ReferenceArea,
    ReferenceDot,
    ReferenceLine,
    ResponsiveContainer,
    Tooltip,
    XAxis,
    YAxis,
} from "recharts";
import {chartTooltipProps} from "@components/charts/chartTheme";
import {brand} from "@components/common/brand/Colors";
import {
    formatReadingValue,
    readingJudgement,
    readingName,
    SCORECARD_HISTORY_DAYS,
    sparklineDomain,
    sparklinePoints,
    sparklineZone,
} from "@components/extension/scorecard/scorecardModel";

const DOT_COLORS = {
    MET: 'var(--ot-scorecard-met)',
    MISSED: 'var(--ot-scorecard-missed)',
}

/**
 * The trend of a reading: a line of its daily snapshots, as wide as its container. A day the
 * reading was unknown breaks the line.
 *
 * With a target, the target is a dashed line and the zone meeting it is shaded - below the line
 * when lower is better, above it when higher is - and today's point is a dot in the colour of the
 * judgement. `target={false}` draws the line only.
 */
export default function ReadingSparkline({reading, height = 56, target = true, axisLabels = false}) {

    const points = sparklinePoints(reading)
    const known = points.filter(it => it.value !== null)
    const name = readingName(reading.key)

    if (known.length < 2) {
        return (
            <SecondaryText data-testid="reading-sparkline-none">
                Not enough daily snapshots for a trend yet
            </SecondaryText>
        )
    }

    const format = (value) => formatReadingValue(reading.key, value)
    const first = known[0]
    const last = known[known.length - 1]
    const today = points[points.length - 1]
    const hasTarget = target && typeof reading.target === 'number'
    const zone = hasTarget ? sparklineZone(reading) : null

    return (
        <div
            role="img"
            aria-label={`${name}, daily, from ${format(first.value)} on ${first.day} to ${format(last.value)} on ${last.day}`}
            data-testid="reading-sparkline"
            data-zone={zone ?? undefined}
            style={{width: '100%'}}
        >
            {/* The initial size is what a test renders: jsdom never reports the size of the container */}
            <ResponsiveContainer width="100%" height={height} initialDimension={{width: 260, height}}>
                <LineChart data={points} margin={{top: 4, right: 5, bottom: 4, left: 5}}>
                    <XAxis dataKey="day" hide/>
                    <YAxis hide domain={sparklineDomain(points, hasTarget ? reading.target : null)}/>
                    <Tooltip formatter={(value) => [format(value), name]} {...chartTooltipProps}/>
                    {
                        zone &&
                        <ReferenceArea
                            y1={zone === 'below' ? undefined : reading.target}
                            y2={zone === 'below' ? reading.target : undefined}
                            fill="var(--ot-scorecard-met-zone)"
                            fillOpacity={1}
                            ifOverflow="extendDomain"
                        />
                    }
                    {
                        hasTarget &&
                        <ReferenceLine
                            y={reading.target}
                            stroke="var(--ot-chart-axis)"
                            strokeDasharray="4 3"
                            ifOverflow="extendDomain"
                        />
                    }
                    <Line
                        type="monotone"
                        dataKey="value"
                        name={name}
                        stroke={brand.series.purple}
                        strokeWidth={2}
                        dot={false}
                        connectNulls={false}
                        isAnimationActive={false}
                    />
                    {
                        today.value !== null &&
                        <ReferenceDot
                            x={today.day}
                            y={today.value}
                            r={height > 60 ? 4 : 3}
                            fill={DOT_COLORS[readingJudgement(reading)] ?? brand.series.purple}
                            stroke="none"
                        />
                    }
                </LineChart>
            </ResponsiveContainer>
            {
                axisLabels &&
                <div style={{display: 'flex', justifyContent: 'space-between', fontSize: 11}}>
                    <SecondaryText style={{fontSize: 11}}>{SCORECARD_HISTORY_DAYS} days ago</SecondaryText>
                    <SecondaryText style={{fontSize: 11}}>today</SecondaryText>
                </div>
            }
        </div>
    )
}
