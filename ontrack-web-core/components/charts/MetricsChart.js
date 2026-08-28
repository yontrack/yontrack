import {useGraphQLClient} from "@components/providers/ConnectionContextProvider";
import {useEffect, useState} from "react";
import ChartContainer from "@components/charts/ChartContainer";
import {chartAxisProps, chartGridProps, chartLegendProps, chartTooltipProps} from "@components/charts/chartTheme";
import {CartesianGrid, ComposedChart, Legend, Line, Tooltip, XAxis, YAxis} from "recharts";

export default function MetricsChart({query, variables}) {

    const client = useGraphQLClient()

    const [chart, setChart] = useState({
        categories: []
    })
    const [dataPoints, setDataPoints] = useState([])

    useEffect(() => {
        if (client) {
            client.request(
                query,
                variables
            ).then(data => {
                const chart = data.getChart

                setChart(chart)
                setDataPoints(
                    chart.dates.map((date, index) => {
                        const point = {
                            date
                        }
                        chart.metricNames.forEach((metricName) => {
                            point[metricName] = chart.metricValues[index]?.[metricName]
                        })
                        return point
                    })
                )
            })
        }
    }, [client, query, variables]);

    const [inactiveSeries, setInactiveSeries] = useState([])

    const legendFormatter = (value, entry, index) => {
        return chart.metricNames[index]
    }

    const legendClick = ({dataKey}) => {
        if (inactiveSeries.includes(dataKey)) {
            setInactiveSeries(inactiveSeries.filter(el => el !== dataKey));
        } else {
            setInactiveSeries(prev => [...prev, dataKey]);
        }
    }

    return (
        <>
            <ChartContainer>
                <ComposedChart
                    data={dataPoints}
                >
                    <CartesianGrid strokeDasharray="3 3" {...chartGridProps}/>
                    <XAxis dataKey="date" angle={-45} tickMargin={30} height={80} interval="preserveStart" {...chartAxisProps}/>
                    <YAxis {...chartAxisProps}/>
                    <Tooltip {...chartTooltipProps}/>
                    <Legend formatter={legendFormatter} onClick={legendClick} style={{cursor: 'pointer'}} {...chartLegendProps}/>
                    {
                        chart && chart.metricNames &&
                        chart.metricNames.map((metricName, metricIndex) => (
                            <>
                                <Line type="monotone"
                                      connectNulls={true}
                                      dataKey={metricName}
                                      stroke={chart.metricColors && chart.metricColors[metricIndex]}
                                      hide={inactiveSeries.includes(metricName)}
                                />
                            </>
                        ))
                    }
                </ComposedChart>
            </ChartContainer>
        </>
    )
}