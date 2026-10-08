import "@testing-library/jest-dom"
import {render, screen, within} from "@testing-library/react"
import AgentActionsView from "@components/extension/agents/AgentActionsView"

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

jest.mock("../../../../components/services/GraphQL", () => ({
    useQuery: (...args) => mockUseQuery(...args),
    callGraphQL: jest.fn(),
}))

jest.mock("../../../../components/extension/license/useLicensedFeature", () => ({
    useLicensedFeature: (...args) => mockUseLicensedFeature(...args),
}))

const action = (id, {project = {id: 1, name: "P"}, sessionLink = "https://claude.ai/code/session-9"} = {}) => ({
    id,
    time: "2026-10-08T10:00:00",
    user: "claude[agent]",
    message: `Build <a href="/build/42">42</a> has been created (${id}).`,
    eventType: {id: "new_build", description: "New build"},
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
 * The actions query answers with the actions, the other ones (agents, event types) with nothing.
 *
 * The same answers on every render, as the hook gives them: the table appends a page it has not seen
 * yet.
 */
const actions = (items, nextPage = null) => {
    const page = {
        data: {userNode: {pageInfo: {nextPage}, pageItems: items}, offset: 0},
        loading: false,
        finished: true,
    }
    const nothing = {data: [], loading: false, finished: true}
    mockUseQuery.mockImplementation((query) => query.includes("agentActions") ? page : nothing)
}

const actionsQuery = () => mockUseQuery.mock.calls.filter(([query]) => query.includes("agentActions")).at(-1)

describe('The latest agent actions', () => {

    beforeEach(() => {
        mockUseQuery.mockReset()
        mockUseLicensedFeature.mockReset()
    })

    it('says that the licence is needed, and reads nothing, when the licence is off', () => {
        mockUseLicensedFeature.mockReturnValue({enabled: false, loading: false})
        actions([action(1)])
        render(<AgentActionsView initialFilter={{days: 7}}/>)
        expect(screen.getByTestId('agent-actions-licence')).toHaveTextContent('Requires the Agent governance licence')
        expect(screen.queryByTestId('agent-actions')).not.toBeInTheDocument()
        expect(mockUseQuery).not.toHaveBeenCalled()
    })

    it('lists the actions with their time, agent, message, project and session', () => {
        mockUseLicensedFeature.mockReturnValue({enabled: true, loading: false})
        actions([action(2), action(1, {project: null, sessionLink: null})])
        render(<AgentActionsView initialFilter={{days: 7}}/>)

        const table = screen.getByTestId('agent-actions')
        const row = table.querySelector('tr[data-row-key="2"]')
        expect(row).toHaveTextContent('Build 42 has been created (2).')
        expect(within(row).getByTestId('agent-actions-actor-2')).toHaveTextContent('Claude, owned by alice@example.com')
        expect(within(row).getByRole('link', {name: 'P'})).toHaveAttribute('href', '/project/1')
        expect(within(row).getByRole('link', {name: 'Agent session'}))
            .toHaveAttribute('href', 'https://claude.ai/code/session-9')

        const other = table.querySelector('tr[data-row-key="1"]')
        expect(within(other).queryByRole('link', {name: 'Agent session'})).not.toBeInTheDocument()
    })

    it('starts from the filter of the URL', () => {
        mockUseLicensedFeature.mockReturnValue({enabled: true, loading: false})
        actions([])
        render(<AgentActionsView initialFilter={{days: 30, eventTypes: ["new_build"], project: "P"}}/>)
        const variables = actionsQuery()[1].variables
        expect(variables).toEqual(expect.objectContaining({
            eventTypes: ["new_build"],
            project: "P",
            offset: 0,
            size: 20,
        }))
        // 30 days before now
        const days = (Date.now() - new Date(variables.from).getTime()) / (24 * 60 * 60 * 1000)
        expect(Math.round(days)).toBe(30)
        expect(variables.agent).toBeUndefined()
    })
})
