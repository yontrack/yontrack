import "@testing-library/jest-dom"
import {render, screen, within} from "@testing-library/react"
import {DashboardWidgetCellContext} from "@components/dashboards/DashboardWidgetCellContextProvider"
import AgentActivityWidget from "@components/widgets/extension/agents/AgentActivityWidget"

// antd's grid asks for the media queries of its responsive columns, which jsdom does not answer
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

const mockUseQuery = jest.fn()
const mockUseLicensedFeature = jest.fn()

jest.mock("../../../../../components/services/GraphQL", () => ({
    useQuery: (...args) => mockUseQuery(...args),
}))

jest.mock("../../../../../components/extension/license/useLicensedFeature", () => ({
    useLicensedFeature: (...args) => mockUseLicensedFeature(...args),
}))

const stats = {
    window: 90,
    from: "2026-07-10T10:00:00",
    builds: 12,
    promotions: 3,
    deployments: 0,
    assistedBuilds: 4,
    knownBuilds: 10,
    unknownBuilds: 7,
    assistedShare: 0.4,
}

const renderWidget = (props) => {
    mockUseQuery.mockReturnValue({data: stats, loading: false, finished: true, error: null})
    const setTitle = jest.fn()
    render(
        <DashboardWidgetCellContext.Provider value={{setTitle}}>
            <AgentActivityWidget {...props}/>
        </DashboardWidgetCellContext.Provider>
    )
    return {setTitle}
}

describe('The agent activity widget', () => {

    beforeEach(() => {
        mockUseQuery.mockReset()
        mockUseLicensedFeature.mockReset()
    })

    it('says that the licence is needed, and reads nothing, when the licence is off', () => {
        mockUseLicensedFeature.mockReturnValue({enabled: false, loading: false})
        renderWidget({})
        expect(screen.getByTestId('agent-activity-widget-licence')).toHaveTextContent('Requires the Agent governance licence')
        expect(mockUseQuery).not.toHaveBeenCalled()
    })

    it('shows the four tiles, each linking to the actions it counts', () => {
        mockUseLicensedFeature.mockReturnValue({enabled: true, loading: false})
        const {setTitle} = renderWidget({window: 90, projects: ["P"]})

        expect(setTitle).toHaveBeenCalledWith("Agent activity - last 90 days")
        const query = mockUseQuery.mock.calls[0]
        expect(query[1].variables).toEqual({window: 90, projects: ["P"], labels: null})

        const builds = screen.getByTestId('agent-activity-builds')
        expect(builds).toHaveTextContent('Builds by agents12')
        expect(within(builds).getByRole('link', {name: 'Builds by agents: 12'}))
            .toHaveAttribute('href', '/extension/agents/actions?window=90&eventTypes=new_build&project=P')
        expect(screen.getByTestId('agent-activity-promotions-value')).toHaveTextContent('3')
        expect(screen.getByTestId('agent-activity-deployments-value')).toHaveTextContent('0')

        expect(screen.getByTestId('agent-activity-assisted-value')).toHaveTextContent('40%')
        expect(screen.getByTestId('agent-activity-assisted-detail')).toHaveTextContent('4 of 10 builds')
        expect(screen.getByTestId('agent-activity-assisted-extra')).toHaveTextContent('7 unknown')
    })

    it('counts the last 30 days across every visible project by default', () => {
        mockUseLicensedFeature.mockReturnValue({enabled: true, loading: false})
        const {setTitle} = renderWidget({})
        expect(setTitle).toHaveBeenCalledWith("Agent activity - last 30 days")
        expect(mockUseQuery.mock.calls[0][1].variables).toEqual({window: 30, projects: null, labels: null})
    })
})
