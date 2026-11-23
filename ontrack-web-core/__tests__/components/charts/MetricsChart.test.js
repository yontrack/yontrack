import React from "react"
import {render, screen} from "@testing-library/react"
import "@testing-library/jest-dom"

// The chart as `getChart` returns it for a CHML stamp (#2175): four series, values on two dates
const mockChmlChart = {
    dates: ["2026-11-20", "2026-11-22"],
    metricNames: ["critical", "high", "medium", "low"],
    metricColors: ["#ee6666", "#fac858", "#5470c6", "#91cc75"],
    metricValues: [
        {critical: 0, high: 5, medium: 2, low: 0},
        {critical: 0, high: 0, medium: 0, low: 0},
    ],
}

// The answer of the server, through the chart's own `dataFn`
jest.mock("../../../components/services/GraphQL", () => ({
    useQuery: (query, {dataFn}) => ({data: dataFn({getChart: mockChmlChart})}),
}))

// `ResponsiveContainer` measures its parent, which jsdom lays out at 0×0: the chart is given a
// fixed size instead, so that it draws.
jest.mock("../../../components/charts/ChartContainer", () => {
    const React = require("react")
    return function ChartContainer({children}) {
        return React.cloneElement(children, {width: 800, height: 400})
    }
})

import MetricsChart from "@components/charts/MetricsChart"

describe("MetricsChart", () => {

    it("draws one series per metric, named in the legend", () => {
        const {container} = render(<MetricsChart query="query" variables={{}}/>)

        mockChmlChart.metricNames.forEach(name => {
            expect(screen.getByText(name)).toBeInTheDocument()
        })
        expect(container.querySelectorAll(".recharts-line")).toHaveLength(4)
    })

})
