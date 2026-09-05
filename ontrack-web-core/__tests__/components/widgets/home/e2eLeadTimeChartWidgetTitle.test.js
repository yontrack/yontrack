import React from "react";
import {render, screen} from "@testing-library/react";

// Ant Design uses window.matchMedia for responsive features; jsdom doesn't provide it
Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: jest.fn().mockImplementation(query => ({
        matches: false,
        media: query,
        onchange: null,
        addListener: jest.fn(),
        removeListener: jest.fn(),
        addEventListener: jest.fn(),
        removeEventListener: jest.fn(),
        dispatchEvent: jest.fn(),
    })),
})
import '@testing-library/jest-dom';

const mockLevels = {
    BRONZE: {
        id: "5",
        name: "BRONZE",
        image: false,
        branch: {id: "3", name: "main", displayName: "main", project: {id: "1", name: "petclinic"}},
    },
    GOLD: {
        id: "12",
        name: "GOLD",
        image: false,
        branch: {id: "8", name: "main", displayName: "main", project: {id: "2", name: "petclinic-ui"}},
    },
}

// The widget resolves each end of the chart through its own query; the resolution is not what this
// test is about, and the chart itself would only pull in an ECharts canvas jsdom cannot draw
jest.mock("../../../../components/widgets/home/promotionChartUtils", () => ({
    usePromotionLevel: (project, branch, promotionLevel) => mockLevels[promotionLevel],
}))
jest.mock("../../../../components/promotionLevels/E2ELeadTimeChart", () => () => null)

import {EventsContext} from "@components/common/EventsContext";
import {DashboardWidgetCellContext} from "@components/dashboards/DashboardWidgetCellContextProvider";
import E2ELeadTimeChartWidget from "@components/widgets/home/E2ELeadTimeChartWidget";

beforeEach(() => {
    // The entity icon fetches its image on mount
    global.fetch = jest.fn().mockResolvedValue({
        ok: true,
        json: () => Promise.resolve({dataURL: 'data:image/png;base64,AAAA'}),
    })
})

/**
 * The end-to-end lead time widget builds its own title rather than going through
 * `PromotionChartTitle`: it spans two promotion levels on two different branches, so the shared
 * title has nothing to say about it. That makes it a third call site for the same linking rule,
 * hence its own test.
 *
 * The title is not rendered by the widget - it is handed to the dashboard cell through the context,
 * so the test captures it and renders it on its own.
 */
describe('E2ELeadTimeChartWidget title', () => {

    const renderTitle = () => {
        const setTitle = jest.fn()
        render(
            <EventsContext.Provider value={{fireEvent: jest.fn(), subscribeToEvent: jest.fn()}}>
                <DashboardWidgetCellContext.Provider value={{setTitle}}>
                    <E2ELeadTimeChartWidget
                        project="petclinic" branch="main" promotionLevel="BRONZE"
                        targetProject="petclinic-ui" targetBranch="main" targetPromotionLevel="GOLD"
                        interval="3m" period="1w"
                    />
                </DashboardWidgetCellContext.Provider>
            </EventsContext.Provider>
        )
        return render(
            <EventsContext.Provider value={{fireEvent: jest.fn(), subscribeToEvent: jest.fn()}}>
                {setTitle.mock.calls.at(-1)[0]}
            </EventsContext.Provider>
        )
    }

    it('links both promotion levels alongside their branches and projects', () => {
        renderTitle()
        expect(screen.getAllByRole('link').map(link => link.getAttribute('href'))).toEqual([
            '/project/1', '/branch/3', '/promotionLevel/5',
            '/project/2', '/branch/8', '/promotionLevel/12',
        ])
    })

    it('keeps naming both ends of the chart', () => {
        renderTitle()
        expect(screen.getByRole('link', {name: /BRONZE/})).toBeVisible()
        expect(screen.getByRole('link', {name: /GOLD/})).toBeVisible()
    })
})
