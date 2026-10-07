import "@testing-library/jest-dom"
import {fireEvent, render, screen} from "@testing-library/react"
import CommitAssistedMarker from "@components/extension/scm/assistants/CommitAssistedMarker"

const claude = {name: "Claude Code", sessionLink: "https://claude.ai/code/session_01"}
const codex = {name: "Codex", sessionLink: null}
const copilot = {name: "Copilot", sessionLink: null}

describe('Marker of an assisted commit', () => {

    it('renders nothing for a commit without assistants', () => {
        const {container} = render(<CommitAssistedMarker assistants={[]} testId="marker"/>)
        expect(container).toBeEmptyDOMElement()
    })

    it('renders nothing when the assistants are not known', () => {
        const {container} = render(<CommitAssistedMarker testId="marker"/>)
        expect(container).toBeEmptyDOMElement()
    })

    it('says "assisted" in words, colour never being the only cue', () => {
        render(<CommitAssistedMarker assistants={[codex]} testId="marker"/>)
        expect(screen.getByTestId('marker')).toHaveTextContent('assisted')
    })

    it('is named after its assistant for a screen reader when it links nowhere', () => {
        render(<CommitAssistedMarker assistants={[codex]} testId="marker"/>)
        expect(screen.getByRole('img', {name: 'Assisted by Codex'})).toBeInTheDocument()
        expect(screen.queryByRole('link')).not.toBeInTheDocument()
    })

    it('links to the agent session in a new tab, without giving it access to the opener', () => {
        render(<CommitAssistedMarker assistants={[claude]} testId="marker"/>)
        const link = screen.getByRole('link', {name: 'Assisted by Claude Code'})
        expect(link).toHaveAttribute('href', 'https://claude.ai/code/session_01')
        expect(link).toHaveAttribute('target', '_blank')
        expect(link).toHaveAttribute('rel', 'noopener noreferrer')
    })

    it('names all its assistants', () => {
        render(<CommitAssistedMarker assistants={[codex, copilot]} testId="marker"/>)
        expect(screen.getByRole('img', {name: 'Assisted by Codex, Copilot'})).toBeInTheDocument()
    })

    it('lists its assistants in a tooltip', async () => {
        render(<CommitAssistedMarker assistants={[codex, copilot]} testId="marker"/>)
        fireEvent.mouseEnter(screen.getByTestId('marker'))
        const tooltip = await screen.findByRole('tooltip')
        expect(tooltip).toHaveTextContent('Codex')
        expect(tooltip).toHaveTextContent('Copilot')
    })

    it('says in its tooltip that it opens the agent session', async () => {
        render(<CommitAssistedMarker assistants={[claude]} testId="marker"/>)
        fireEvent.mouseEnter(screen.getByRole('link', {name: 'Assisted by Claude Code'}))
        const tooltip = await screen.findByRole('tooltip')
        expect(tooltip).toHaveTextContent('Assisted by Claude Code')
        expect(tooltip).toHaveTextContent('Opens the agent session in a new tab.')
    })
})
