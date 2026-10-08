import "@testing-library/jest-dom"
import {render, screen, within} from "@testing-library/react"
import AgentActivity from "@components/core/admin/agents/AgentActivity"

// antd's Table asks for the media queries of its responsive columns, which jsdom does not answer
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
    callGraphQL: jest.fn(),
}))

jest.mock("../../../../../components/extension/license/useLicensedFeature", () => ({
    useLicensedFeature: (...args) => mockUseLicensedFeature(...args),
}))

const agent = {id: 12}

const action = (id, {project = {id: 1, name: "P"}, sessionLink = "https://claude.ai/code/session-9"} = {}) => ({
    id,
    time: "2026-10-08T10:00:00",
    message: `Build <a href="/build/42">42</a> has been validated (${id}).`,
    eventType: {id: "new_validation_run", description: "New validation run"},
    project,
    actor: {
        kind: "AGENT",
        agent: "claude[agent]",
        displayName: "Claude",
        tool: "Claude Code",
        owner: "alice@example.com",
        sessionId: "session-9",
        sessionLink,
    },
})

/**
 * The activity query answers with the actions, the event types query (of the filter) with nothing.
 */
const activity = (actions, nextPage = null) => {
    const page = {
        data: {
            offset: 0,
            activity: {
                pageInfo: {nextPage},
                pageItems: actions,
            },
        },
        loading: false,
    }
    mockUseQuery.mockImplementation((query) =>
        query.includes("agentActivity") ? page : {data: [], loading: false, finished: true}
    )
}

describe('The activity of an agent', () => {

    beforeEach(() => {
        mockUseQuery.mockReset()
        mockUseLicensedFeature.mockReset()
    })

    it('says that the licence is needed, and reads nothing, when the licence is off', () => {
        mockUseLicensedFeature.mockReturnValue({enabled: false, loading: false})
        activity([action(1)])
        render(<AgentActivity agent={agent}/>)
        expect(screen.getByTestId('agent-activity-licence')).toHaveTextContent('Requires the Agent governance licence')
        expect(screen.queryByTestId('agent-activity')).not.toBeInTheDocument()
        expect(mockUseQuery).not.toHaveBeenCalled()
    })

    it('says nothing while the licence is not known', () => {
        mockUseLicensedFeature.mockReturnValue({enabled: undefined, loading: true})
        render(<AgentActivity agent={agent}/>)
        expect(screen.queryByTestId('agent-activity-licence')).not.toBeInTheDocument()
        expect(screen.queryByTestId('agent-activity')).not.toBeInTheDocument()
    })

    it('lists the actions with their time, message, project and session', () => {
        mockUseLicensedFeature.mockReturnValue({enabled: true, loading: false})
        activity([action(2), action(1, {project: null, sessionLink: null})])
        render(<AgentActivity agent={agent}/>)

        const row = screen.getByTestId('agent-activity-2')
        expect(row).toHaveTextContent('Build 42 has been validated (2).')
        expect(within(row).getByRole('link', {name: 'P'})).toHaveAttribute('href', '/project/1')
        expect(within(row).getByRole('link', {name: 'Agent session'}))
            .toHaveAttribute('href', 'https://claude.ai/code/session-9')

        const other = screen.getByTestId('agent-activity-1')
        expect(within(other).queryByRole('link', {name: 'Agent session'})).not.toBeInTheDocument()

        // The last 7 days, by default
        const query = mockUseQuery.mock.calls.find(([query]) => query.includes("agentActivity"))
        expect(query[1].variables).toEqual(expect.objectContaining({id: 12, offset: 0, size: 20}))
        expect(query[1].variables.from).toBeDefined()
        expect(screen.getByTestId('agent-activity-window')).toHaveTextContent('7 days30 days90 days')
        // No load more without a next page
        expect(screen.queryByRole('button', {name: /Load more/})).not.toBeInTheDocument()
    })

    it('offers to load more actions when there are more', () => {
        mockUseLicensedFeature.mockReturnValue({enabled: true, loading: false})
        activity([action(2)], {offset: 20, size: 20})
        render(<AgentActivity agent={agent}/>)
        expect(screen.getByRole('button', {name: /Load more/})).toBeEnabled()
    })

    it('says when the agent did nothing in the window', () => {
        mockUseLicensedFeature.mockReturnValue({enabled: true, loading: false})
        activity([])
        render(<AgentActivity agent={agent}/>)
        expect(screen.getByText('No action by this agent in the last 7 days.')).toBeInTheDocument()
    })
})
