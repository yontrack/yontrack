import {Typography} from "antd";
import {Line, LineChart, ReferenceLine, Tooltip, XAxis, YAxis} from "recharts";
import {chartTooltipProps} from "@components/charts/chartTheme";
import {brand} from "@components/common/brand/Colors";
import {formatReadingValue, readingName, sparklinePoints} from "@components/extension/scorecard/scorecardModel";

/**
 * The trend of a reading: a line of its daily snapshots, the target of its estate dashed when
 * there is one. A day the reading was unknown breaks the line.
 */
export default function ReadingSparkline({reading, width = 260, height = 56}) {

    const points = sparklinePoints(reading)
    const known = points.filter(it => it.value !== null)
    const name = readingName(reading.key)

    if (known.length < 2) {
        return (
            <Typography.Text type="secondary" data-testid="reading-sparkline-none">
                Not enough daily snapshots for a trend yet
            </Typography.Text>
        )
    }

    const format = (value) => formatReadingValue(reading.key, value)
    const first = known[0]
    const last = known[known.length - 1]

    return (
        <div
            role="img"
            aria-label={`${name}, daily, from ${format(first.value)} on ${first.day} to ${format(last.value)} on ${last.day}`}
            data-testid="reading-sparkline"
        >
            <LineChart width={width} height={height} data={points} margin={{top: 4, right: 4, bottom: 4, left: 4}}>
                <XAxis dataKey="day" hide/>
                <YAxis hide domain={['auto', 'auto']}/>
                <Tooltip formatter={(value) => [format(value), name]} {...chartTooltipProps}/>
                {
                    typeof reading.target === 'number' &&
                    <ReferenceLine
                        y={reading.target}
                        stroke="var(--ot-chart-axis)"
                        strokeDasharray="4 4"
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
            </LineChart>
        </div>
    )
}
