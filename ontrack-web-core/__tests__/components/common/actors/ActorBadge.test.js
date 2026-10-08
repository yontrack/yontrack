import "@testing-library/jest-dom"
import {fireEvent, render, screen} from "@testing-library/react"
import ActorBadge from "@components/common/actors/ActorBadge"
import {agentAccessibleName, agentText, isAgentSignature} from "@components/common/actors/actors"

const person = {user: "alice", time: "2026-10-08T10:00:00Z", actor: null}

const actor = {
    kind: "agent",
    agent: "claude[agent]",
    displayName: "Claude",
    tool: "Claude Code",
    owner: "alice@example.com",
    sessionId: "session-1",
    sessionLink: "https://claude.ai/code/session-1",
}

const agent = {user: "claude[agent]", time: "2026-10-08T10:00:00Z", actor}

const agentWithoutSession = {
    ...agent,
    actor: {...actor, sessionId: null, sessionLink: null, tool: null},
}

describe('Actor helpers', () => {

    it('tells an agent from a person', () => {
        expect(isAgentSignature(agent)).toBe(true)
        expect(isAgentSignature(person)).toBe(false)
        expect(isAgentSignature({user: "alice"})).toBe(false)
        expect(isAgentSignature(null)).toBe(false)
    })

    it('names the agent and its owner', () => {
        expect(agentText(actor)).toBe("Claude, owned by alice@example.com")
    })

    it('says "agent" in the accessible name', () => {
        expect(agentAccessibleName(actor)).toBe("agent Claude, owned by alice@example.com")
        expect(agentAccessibleName(actor, "by")).toBe("by agent Claude, owned by alice@example.com")
    })
})

describe('ActorBadge', () => {

    describe('for a person', () => {

        it('renders the user and nothing else, as before', () => {
            const {container} = render(<ActorBadge signature={person} testId="actor"/>)
            expect(container).toHaveTextContent(/^alice$/)
            expect(screen.queryByTestId("actor")).not.toBeInTheDocument()
            expect(screen.queryByRole("img")).not.toBeInTheDocument()
            expect(screen.queryByRole("link")).not.toBeInTheDocument()
        })

        it('renders the user of a signature without any actor field', () => {
            const {container} = render(<ActorBadge signature={{user: "bob"}}/>)
            expect(container).toHaveTextContent(/^bob$/)
        })

        it('puts the prefix before the user', () => {
            const {container} = render(<ActorBadge signature={person} prefix="by"/>)
            expect(container).toHaveTextContent(/^by alice$/)
        })

        it('renders nothing when asked to hide persons', () => {
            const {container} = render(<ActorBadge signature={person} hideHuman={true}/>)
            expect(container).toBeEmptyDOMElement()
        })

        it('renders nothing without a signature or a user', () => {
            expect(render(<ActorBadge/>).container).toBeEmptyDOMElement()
            expect(render(<ActorBadge signature={{time: "2026-10-08T10:00:00Z"}}/>).container).toBeEmptyDOMElement()
        })
    })

    describe('for an agent', () => {

        it('names the agent and its owner in words, beside a robot icon', () => {
            render(<ActorBadge signature={agentWithoutSession} testId="actor"/>)
            const badge = screen.getByTestId("actor")
            expect(badge).toHaveTextContent("Claude, owned by alice@example.com")
            expect(badge.querySelector("svg")).toHaveAttribute("aria-hidden", "true")
        })

        it('says it is an agent to a screen reader, colour and icon never being the only cue', () => {
            render(<ActorBadge signature={agentWithoutSession} testId="actor"/>)
            expect(screen.getByRole("img", {name: "agent Claude, owned by alice@example.com"})).toBeInTheDocument()
        })

        it('puts the prefix in the badge and in its accessible name', () => {
            render(<ActorBadge signature={agentWithoutSession} prefix="by" testId="actor"/>)
            expect(screen.getByTestId("actor")).toHaveTextContent("by Claude, owned by alice@example.com")
            expect(screen.getByRole("img", {name: "by agent Claude, owned by alice@example.com"})).toBeInTheDocument()
        })

        it('is shown even when persons are hidden', () => {
            render(<ActorBadge signature={agentWithoutSession} hideHuman={true} testId="actor"/>)
            expect(screen.getByTestId("actor")).toBeInTheDocument()
        })

        it('links to the agent session in a new tab, without giving it access to the opener', () => {
            render(<ActorBadge signature={agent} testId="actor"/>)
            const link = screen.getByRole("link", {name: "agent Claude, owned by alice@example.com"})
            expect(link).toHaveAttribute("href", "https://claude.ai/code/session-1")
            expect(link).toHaveAttribute("target", "_blank")
            expect(link).toHaveAttribute("rel", "noopener noreferrer")
        })

        it('does not link when asked not to, inside another link', () => {
            render(<ActorBadge signature={agent} link={false} testId="actor"/>)
            expect(screen.queryByRole("link")).not.toBeInTheDocument()
            expect(screen.getByRole("img", {name: "agent Claude, owned by alice@example.com"})).toBeInTheDocument()
        })

        it('shows the tool and the session in its tooltip', async () => {
            render(<ActorBadge signature={agent} testId="actor"/>)
            fireEvent.mouseEnter(screen.getByTestId("actor"))
            const tooltip = await screen.findByRole("tooltip")
            expect(tooltip).toHaveTextContent("Agent claude[agent]")
            expect(tooltip).toHaveTextContent("Owned by alice@example.com")
            expect(tooltip).toHaveTextContent("Tool: Claude Code")
            expect(tooltip).toHaveTextContent("Session: session-1")
            expect(tooltip).toHaveTextContent("Opens the agent session in a new tab.")
        })

        it('leaves the tool and the session out of its tooltip when they are not known', async () => {
            render(<ActorBadge signature={agentWithoutSession} testId="actor"/>)
            fireEvent.mouseEnter(screen.getByTestId("actor"))
            const tooltip = await screen.findByRole("tooltip")
            expect(tooltip).toHaveTextContent("Owned by alice@example.com")
            expect(tooltip).not.toHaveTextContent("Tool:")
            expect(tooltip).not.toHaveTextContent("Session")
        })
    })
})
