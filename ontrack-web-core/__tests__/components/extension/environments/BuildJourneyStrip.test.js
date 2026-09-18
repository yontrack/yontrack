import "@testing-library/jest-dom"
import {fireEvent, render, screen} from "@testing-library/react"

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

// `SlotAdmissionRuleSummary` goes through `Dynamic`, whose webpack context does not exist under Jest.
jest.mock("../../../../components/extension/environments/SlotAdmissionRuleSummary", () => ({
    __esModule: true,
    default: ({ruleId}) => <span>{ruleId}</span>,
}))

import BuildJourneyStrip from "@components/extension/environments/journey/BuildJourneyStrip"

const entry = (name, order, state, {nonEligibleRules = []} = {}) => ({
    state,
    nonEligibleRules,
    pipeline: null,
    slot: {
        id: `slot-${name}`,
        qualifier: '',
        environment: {id: `env-${name}`, name, order, image: false},
        project: {id: 1, name: 'petclinic'},
    },
})

describe('the build journey strip', () => {

    it('draws one chip per slot, in the order it was given', () => {
        render(<BuildJourneyStrip journey={[
            entry('staging', 10, 'SUPERSEDED'),
            entry('production', 20, 'DEPLOYED'),
        ]}/>)
        const chips = screen.getAllByTestId(/^journey-chip-/)
        expect(chips.map(chip => chip.getAttribute('data-testid')))
            .toEqual(['journey-chip-slot-staging', 'journey-chip-slot-production'])
        expect(chips[0]).toHaveTextContent('staging — Superseded')
        expect(chips[1]).toHaveTextContent('production — Deployed')
    })

    it('keeps the environments which refuse the build', () => {
        // An environment missing from the strip cannot be told from one which does not exist, and
        // "why is this build not in production?" is the question the strip answers.
        render(<BuildJourneyStrip journey={[
            entry('production', 20, 'NOT_ELIGIBLE', {
                nonEligibleRules: [{id: 'r1', name: 'gold', ruleId: 'promotion', ruleConfig: {}}],
            }),
        ]}/>)
        expect(screen.getByTestId('journey-chip-slot-production')).toHaveTextContent('Not eligible')
    })

    it('draws the environment icon on every chip', () => {
        render(<BuildJourneyStrip journey={[entry('production', 20, 'DEPLOYED')]}/>)
        expect(screen.getByRole('img', {name: 'production'})).toBeVisible()
    })

    it('hands the clicked slot to its caller', () => {
        const onSlotClick = jest.fn()
        const theEntry = entry('production', 20, 'DEPLOYED')
        render(<BuildJourneyStrip journey={[theEntry]} onSlotClick={onSlotClick}/>)
        fireEvent.click(screen.getByTestId('journey-chip-slot-production'))
        expect(onSlotClick).toHaveBeenCalledWith(theEntry.slot)
    })

    it('says so when the project has no slot at all', () => {
        render(<BuildJourneyStrip journey={[]}/>)
        expect(screen.getByTestId('build-journey-empty'))
            .toHaveTextContent('This project has no deployment slot.')
    })
})
