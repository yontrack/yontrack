import "@testing-library/jest-dom"
import {render, screen, within} from "@testing-library/react"
import BuildContentAgents from "@components/builds/BuildContentAgents"
import BuildAgentsAssistedBy from "@components/builds/agents/BuildAgentsAssistedBy"

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

jest.mock("../../../../components/services/GraphQL", () => ({
    useQuery: (...args) => mockUseQuery(...args),
}))

const build = {id: 42}

const assistedChange = {
    assisted: true,
    basis: "COMPUTED",
    unknownReason: null,
    assistants: ["Claude Code", "Codex"],
    assistedCommits: 3,
    totalCommits: 12,
    sessionLinks: ["https://claude.ai/code/session-1", "https://claude.ai/code/session-2"],
    previousBuild: {id: 41, name: "41", displayName: "41"},
}

const agentActor = {
    kind: "AGENT",
    agent: "claude[agent]",
    displayName: "Claude",
    tool: "Claude Code",
    owner: "alice@example.com",
    sessionId: "session-9",
    sessionLink: "https://claude.ai/code/session-9",
}

const action = (id, sessionLink = agentActor.sessionLink) => ({
    id,
    time: "2026-10-08T10:00:00",
    user: "claude[agent]",
    actor: {...agentActor, sessionLink},
    message: `Build <a href="/build/42">42</a> has been validated (${id}).`,
    eventType: {id: "new_validation_run"},
})

const page = (assistedChange, actions, nextPage = null) => ({
    data: {
        offset: 0,
        build: {
            id: 42,
            assistedChange,
            agentActions: {
                pageInfo: {nextPage},
                pageItems: actions,
            },
        },
    },
    loading: false,
})

describe('The Agents section of a build', () => {

    it('shows the assisted change and the actions by agents, apart', () => {
        mockUseQuery.mockReturnValue(page(assistedChange, [action(2), action(1, null)]))
        render(<BuildContentAgents build={build}/>)

        // Assisted by, from git
        const assisted = screen.getByTestId('build-agents-assisted')
        expect(within(assisted).getByRole('heading', {name: 'Assisted by'})).toBeInTheDocument()
        expect(within(assisted).getByText('Claude Code')).toBeInTheDocument()
        expect(within(assisted).getByText('Codex')).toBeInTheDocument()
        expect(within(assisted).getByRole('link', {name: '3 of 12 commits since 41'}))
            .toHaveAttribute('href', '/extension/scm/changelog?from=41&to=42')
        expect(within(assisted).getByRole('link', {name: 'Agent session 1'}))
            .toHaveAttribute('href', 'https://claude.ai/code/session-1')
        expect(within(assisted).getByText('Computed by Yontrack from the change log')).toBeInTheDocument()
        // The assistants are kinds, never registered agents
        expect(within(assisted).queryByText(/owned by/)).not.toBeInTheDocument()

        // Actions by agents, from the events
        const actions = screen.getByTestId('build-agents-actions')
        expect(within(actions).getByRole('heading', {name: 'Actions by agents'})).toBeInTheDocument()
        expect(screen.getByTestId('build-agent-action-2')).toHaveTextContent('Build 42 has been validated (2).')
        expect(screen.getByTestId('build-agent-action-actor-2')).toHaveTextContent('Claude, owned by alice@example.com')
        expect(screen.getByTestId('build-agent-action-session-2'))
            .toHaveAttribute('href', 'https://claude.ai/code/session-9')
        expect(screen.queryByTestId('build-agent-action-session-1')).not.toBeInTheDocument()
        // No load more without a next page
        expect(within(actions).queryByRole('button', {name: /Load more/})).not.toBeInTheDocument()
    })

    it('offers to load more actions when there are more', () => {
        mockUseQuery.mockReturnValue(page(assistedChange, [action(2)], {offset: 1, size: 10}))
        render(<BuildContentAgents build={build}/>)
        expect(within(screen.getByTestId('build-agents-actions')).getByRole('button', {name: /Load more/}))
            .toBeEnabled()
    })

    it('says when no agent acted on an assisted build', () => {
        mockUseQuery.mockReturnValue(page(assistedChange, []))
        render(<BuildContentAgents build={build}/>)
        expect(within(screen.getByTestId('build-agents-actions')).getByText('No action by an agent on this build.'))
            .toBeInTheDocument()
    })
})

describe('Assisted by', () => {

    it('gives the reason of an unknown assisted change', () => {
        render(<BuildAgentsAssistedBy build={build} assistedChange={{
            assisted: false,
            basis: "UNKNOWN",
            unknownReason: "no previous build with a commit",
            assistants: [],
            assistedCommits: null,
            totalCommits: null,
            sessionLinks: [],
            previousBuild: null,
        }}/>)
        expect(screen.getByText('Unknown: no previous build with a commit')).toBeInTheDocument()
        expect(screen.getByText('No assistant known')).toBeInTheDocument()
        expect(screen.queryByTestId('build-agents-commits')).not.toBeInTheDocument()
    })

    it('says a value was set by the CI, without counts', () => {
        render(<BuildAgentsAssistedBy build={build} assistedChange={{
            ...assistedChange,
            basis: "SET_BY_CI",
            assistedCommits: null,
            totalCommits: null,
            sessionLinks: [],
            previousBuild: null,
        }}/>)
        expect(screen.getByText('Set by the CI')).toBeInTheDocument()
        expect(screen.queryByTestId('build-agents-commits')).not.toBeInTheDocument()
        expect(screen.queryByTestId('build-agents-sessions')).not.toBeInTheDocument()
    })

    it('says no commit was assisted', () => {
        render(<BuildAgentsAssistedBy build={build} assistedChange={{
            ...assistedChange,
            assisted: false,
            assistants: [],
            assistedCommits: 0,
            totalCommits: 4,
            sessionLinks: [],
        }}/>)
        expect(screen.getByText('No assisted commit')).toBeInTheDocument()
        expect(screen.getByRole('link', {name: '0 of 4 commits since 41'})).toBeInTheDocument()
    })

    it('says when the assisted change is not computed yet', () => {
        render(<BuildAgentsAssistedBy build={build} assistedChange={null}/>)
        expect(screen.getByText('Not computed yet')).toBeInTheDocument()
    })
})
