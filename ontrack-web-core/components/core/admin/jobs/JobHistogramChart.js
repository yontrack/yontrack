import {Bar, BarChart, Cell, ResponsiveContainer, Tooltip, XAxis} from "recharts";
import {useEffect, useState} from "react";
import dayjs from "dayjs";

// The tooltip is hand-built rather than a themed `<Tooltip/>`, so it carries the
// same theme tokens the shared chart defaults apply everywhere else.
const tooltipStyle = {
    backgroundColor: 'var(--ot-chart-tooltip-bg)',
    border: '1px solid var(--ot-chart-grid)',
    color: 'var(--ot-chart-text)',
    padding: '5px',
}

function formatDuration(durationMs) {
    if (durationMs < 1000) {
        return `${durationMs} ms`
    } else {
        return dayjs.duration(durationMs, 'milliseconds').format('H[h] m[m] s[s]')
    }
}

const JobHistogramTooltip = ({active, payload}) => {
    if (active && payload && payload.length) {
        const {date, displayValue, displayMinValue, displayMaxValue, count, errorCount} = payload[0].payload;
        if (count > 0) {
            return (
                <div style={tooltipStyle}>
                    <div>{dayjs(date).format('YYYY-MM-DD')}</div>
                    <div>
                        <b>Durations:</b>
                    </div>
                    <div>
                        * avg: {displayValue}
                    </div>
                    <div>
                        * min: {displayMinValue}
                    </div>
                    <div>
                        * max: {displayMaxValue}
                    </div>
                    <div>
                        <b>Measures</b>: {count}
                    </div>
                    <div>
                        <b>Errors</b>: {errorCount}
                    </div>
                </div>
            )
        } else {
            return <div style={{...tooltipStyle, fontStyle: 'italic'}}>
                No measures
            </div>
        }
    }
    return null;
}

export default function JobHistogramChart({histogram}) {

    const [dataPoints, setDataPoints] = useState([])
    useEffect(() => {
        setDataPoints(
            histogram.items.map(item => {
                return {
                    date: item.from,
                    value: item.avgDurationMs,
                    displayValue: formatDuration(item.avgDurationMs),
                    displayMinValue: formatDuration(item.minDurationMs),
                    displayMaxValue: formatDuration(item.maxDurationMs),
                    count: item.count,
                    errorCount: item.errorCount,
                    error: item.error,
                }
            })
        )
    }, [histogram])

    return (
        <>
            <ResponsiveContainer style={{
                width: '145px',
                maxWidth: '145px',
                aspectRatio: 4,
                borderBottom: "solid 1px var(--ot-chart-grid)",
            }}>
                <BarChart
                    data={dataPoints}
                >
                    <XAxis dataKey="date" hide={true}/>
                    <Tooltip content={<JobHistogramTooltip/>} cursor={{fill: 'var(--ot-chart-cursor)'}}/>
                    <Bar name="Duration (avg)" dataKey="value">
                        {
                            dataPoints.map((entry, index) => (
                                <Cell key={`cell-${index}`}
                                      fill={entry.error ? 'var(--ot-chart-error)' : 'var(--ot-chart-ok)'}/>
                            ))
                        }
                    </Bar>
                </BarChart>
            </ResponsiveContainer>
        </>
    )
}