import {useState} from "react";
import {useQuery} from "@components/services/GraphQL";
import {formatSeconds} from "@components/common/Duration";
import {Bar, CartesianGrid, ComposedChart, Legend, Line, Tooltip, XAxis, YAxis} from "recharts";
import ChartContainer from "@components/charts/ChartContainer";
import {chartAxisProps, chartGridProps, chartLegendProps, chartTooltipProps} from "@components/charts/chartTheme";
import {brand} from "@components/common/brand/Colors";

const noChart = {
    chart: {
        categories: []
    },
    dataPoints: [],
}

export default function DurationChart({query, variables}) {

    const {data: chartData} = useQuery(query, {
        variables,
        deps: [query, variables],
        initialData: noChart,
        dataFn: data => {
            const chart = data.getChart
            /**
             *
             * categories: [],
             * dates: [],
             * data: {
             *     mean: [],
             *     percentile90: [],
             *     maximum: [],
             * }
             */
            return {
                chart,
                dataPoints: chart.dates.map((date, index) => {
                    return {
                        date,
                        mean: chart.data.mean[index],
                        percentile90: chart.data.percentile90[index],
                        maximum: chart.data.maximum[index],
                    }
                }),
            }
        },
    })
    const {chart, dataPoints} = chartData ?? noChart

    const legendFormatter = (value, entry, index) => {
        return chart.categories[index]
    }

    const [inactiveSeries, setInactiveSeries] = useState([])

    const legendClick = ({dataKey}) => {
        if (inactiveSeries.includes(dataKey)) {
            setInactiveSeries(inactiveSeries.filter(el => el !== dataKey));
        } else {
            setInactiveSeries(prev => [...prev, dataKey]);
        }
    }

    const durationFormatter = (value) => {
        return formatSeconds(value, "-")
    }

    return (
        <>
            <ChartContainer>
                <ComposedChart
                    data={dataPoints}
                >
                    <CartesianGrid strokeDasharray="3 3" {...chartGridProps}/>
                    <XAxis dataKey="date" angle={-45} tickMargin={30} height={80} interval="preserveStart" {...chartAxisProps}/>
                    <YAxis tickFormatter={durationFormatter} {...chartAxisProps}/>
                    <Tooltip formatter={durationFormatter} {...chartTooltipProps}/>
                    <Legend formatter={legendFormatter} onClick={legendClick} style={{cursor: 'pointer'}} {...chartLegendProps}/>
                    <Bar dataKey="mean" fill={brand.series.lilac} hide={inactiveSeries.includes('mean')}/>
                    <Line type="monotone" connectNulls={true} dataKey="percentile90" stroke={brand.series.purple} hide={inactiveSeries.includes('percentile90')}/>
                    <Line type="monotone" connectNulls={true} dataKey="maximum" stroke={brand.series.green} hide={inactiveSeries.includes('maximum')}/>
                </ComposedChart>
            </ChartContainer>
        </>
    )
}