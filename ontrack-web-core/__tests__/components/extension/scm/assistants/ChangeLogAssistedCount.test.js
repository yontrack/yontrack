import "@testing-library/jest-dom"
import {render, screen} from "@testing-library/react"
import ChangeLogAssistedCount from "@components/extension/scm/assistants/ChangeLogAssistedCount"
import {assistedCountText, countAssistedCommits} from "@components/extension/scm/assistants/assistants"

const commit = (...names) => ({
    commit: {
        assistants: names.map(name => ({name, sessionLink: null})),
    },
})

describe('Count of the assisted commits of a change log', () => {

    it('says how many commits out of all of them are assisted', () => {
        render(<ChangeLogAssistedCount commits={[commit("Claude Code"), commit(), commit("Codex", "Copilot")]}/>)
        expect(screen.getByTestId('change-log-assisted-count')).toHaveTextContent('2 of 3 commits assisted')
    })

    it('uses the singular for a single commit', () => {
        render(<ChangeLogAssistedCount commits={[commit("Claude Code")]}/>)
        expect(screen.getByTestId('change-log-assisted-count')).toHaveTextContent('1 of 1 commit assisted')
    })

    it('shows nothing when no commit is assisted', () => {
        const {container} = render(<ChangeLogAssistedCount commits={[commit(), commit()]}/>)
        expect(container).toBeEmptyDOMElement()
    })

    it('shows nothing while the commits are not known', () => {
        const {container} = render(<ChangeLogAssistedCount/>)
        expect(container).toBeEmptyDOMElement()
    })

    it('counts the commits having at least one assistant', () => {
        expect(countAssistedCommits([commit("Claude Code"), commit(), {commit: {}}, commit("A", "B")])).toBe(2)
        expect(countAssistedCommits(undefined)).toBe(0)
    })

    it('words the count', () => {
        expect(assistedCountText(3, 12)).toBe('3 of 12 commits assisted')
        expect(assistedCountText(1, 1)).toBe('1 of 1 commit assisted')
    })
})
