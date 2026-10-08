import "@testing-library/jest-dom"
import {fireEvent, render, screen} from "@testing-library/react"
import AssistedBadge from "@components/extension/scm/assistants/AssistedBadge"
import {assistedChangeSummary} from "@components/extension/scm/assistants/assistants"

const assisted = {
    assisted: true,
    basis: "COMPUTED",
    unknownReason: null,
    assistants: ["Claude Code", "Codex"],
    assistedCommits: 3,
    totalCommits: 12,
    sessionLinks: ["https://claude.ai/code/session-1", "https://claude.ai/code/session-2"],
}

const notAssisted = {
    assisted: false,
    basis: "COMPUTED",
    unknownReason: null,
    assistants: [],
    assistedCommits: 0,
    totalCommits: 4,
    sessionLinks: [],
}

const unknown = {
    assisted: false,
    basis: "UNKNOWN",
    unknownReason: "no previous build with a commit",
    assistants: [],
    assistedCommits: null,
    totalCommits: null,
    sessionLinks: [],
}

describe('Summary of an assisted change', () => {

    it('counts the commits and names the assistants', () => {
        expect(assistedChangeSummary(assisted)).toBe("3 of 12 commits, by Claude Code, Codex")
    })

    it('says "commit" for one commit', () => {
        expect(assistedChangeSummary({...assisted, assistedCommits: 1, totalCommits: 1, assistants: ["Codex"]}))
            .toBe("1 of 1 commit, by Codex")
    })

    it('names the assistants alone when the CI gave no counts', () => {
        expect(assistedChangeSummary({...assisted, basis: "SET_BY_CI", assistedCommits: null, totalCommits: null}))
            .toBe("by Claude Code, Codex")
    })
})

describe('AssistedBadge', () => {

    it('renders nothing when the assisted change has not been computed', () => {
        expect(render(<AssistedBadge assistedChange={null}/>).container).toBeEmptyDOMElement()
        expect(render(<AssistedBadge/>).container).toBeEmptyDOMElement()
    })

    it('renders nothing for a build which is not assisted', () => {
        expect(render(<AssistedBadge assistedChange={notAssisted}/>).container).toBeEmptyDOMElement()
    })

    describe('for an assisted build', () => {

        it('says "Assisted" in words, beside a robot icon', () => {
            render(<AssistedBadge assistedChange={assisted} testId="assisted"/>)
            const badge = screen.getByTestId("assisted")
            expect(badge).toHaveTextContent(/^Assisted$/)
            expect(badge.querySelector("svg")).toHaveAttribute("aria-hidden", "true")
        })

        it('gives the counts and the assistants to a screen reader', () => {
            render(<AssistedBadge assistedChange={assisted} testId="assisted"/>)
            expect(screen.getByRole("img", {name: "Assisted: 3 of 12 commits, by Claude Code, Codex"}))
                .toBeInTheDocument()
        })

        it('can be reached from the keyboard, to show its tooltip', () => {
            render(<AssistedBadge assistedChange={assisted} testId="assisted"/>)
            expect(screen.getByTestId("assisted")).toHaveAttribute("tabindex", "0")
        })

        it('gives the counts, the assistants and the session links in its tooltip', async () => {
            render(<AssistedBadge assistedChange={assisted} testId="assisted"/>)
            fireEvent.mouseEnter(screen.getByTestId("assisted"))
            const tooltip = await screen.findByRole("tooltip")
            expect(tooltip).toHaveTextContent("3 of 12 commits, by Claude Code, Codex")
            const links = tooltip.querySelectorAll("a")
            expect(links).toHaveLength(2)
            expect(links[0]).toHaveAttribute("href", "https://claude.ai/code/session-1")
            expect(links[0]).toHaveAttribute("target", "_blank")
            expect(links[0]).toHaveAttribute("rel", "noopener noreferrer")
            expect(links[0]).toHaveTextContent("Agent session 1")
            expect(links[1]).toHaveAttribute("href", "https://claude.ai/code/session-2")
        })

        it('names a single session link without a number', async () => {
            render(<AssistedBadge
                assistedChange={{...assisted, sessionLinks: ["https://claude.ai/code/session-1"]}}
                testId="assisted"
            />)
            fireEvent.mouseEnter(screen.getByTestId("assisted"))
            const tooltip = await screen.findByRole("tooltip")
            expect(tooltip.querySelector("a")).toHaveTextContent(/^Agent session$/)
        })

        it('has no link in its tooltip without any session', async () => {
            render(<AssistedBadge assistedChange={{...assisted, sessionLinks: []}} testId="assisted"/>)
            fireEvent.mouseEnter(screen.getByTestId("assisted"))
            const tooltip = await screen.findByRole("tooltip")
            expect(tooltip.querySelector("a")).toBeNull()
        })
    })

    describe('for an unknown assisted change', () => {

        it('says "Assisted: unknown" in words, as a neutral tag', () => {
            render(<AssistedBadge assistedChange={unknown} testId="assisted"/>)
            const badge = screen.getByTestId("assisted")
            expect(badge).toHaveTextContent(/^Assisted: unknown$/)
            expect(badge).not.toHaveClass("ant-tag-purple")
        })

        it('gives the reason to a screen reader', () => {
            render(<AssistedBadge assistedChange={unknown} testId="assisted"/>)
            expect(screen.getByRole("img", {name: "Assisted: unknown, no previous build with a commit"}))
                .toBeInTheDocument()
        })

        it('gives the reason in its tooltip', async () => {
            render(<AssistedBadge assistedChange={unknown} testId="assisted"/>)
            fireEvent.mouseEnter(screen.getByTestId("assisted"))
            const tooltip = await screen.findByRole("tooltip")
            expect(tooltip).toHaveTextContent("no previous build with a commit")
        })

        it('says it could not be computed when there is no reason', async () => {
            render(<AssistedBadge assistedChange={{...unknown, unknownReason: null}} testId="assisted"/>)
            expect(screen.getByRole("img", {name: "Assisted: unknown"})).toBeInTheDocument()
            fireEvent.mouseEnter(screen.getByTestId("assisted"))
            const tooltip = await screen.findByRole("tooltip")
            expect(tooltip).toHaveTextContent("Whether the commits of this build were assisted could not be computed.")
        })
    })
})
