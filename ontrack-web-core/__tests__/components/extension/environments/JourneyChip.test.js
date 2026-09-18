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

import JourneyChip from "@components/extension/environments/shared/JourneyChip"

/**
 * The chip ships in phase 1 although its consumers - the journey strip, the decorations, the build
 * search column, the delivery map - arrive in phases 5 and 7. These tests are what stands in for
 * those consumers until they exist.
 */

const entry = (state, {qualifier = '', nonEligibleRules = []} = {}) => ({
    state,
    nonEligibleRules,
    pipeline: null,
    slot: {
        id: 'slot-1',
        qualifier,
        environment: {id: 'env-1', name: 'production', order: 20},
        project: {id: 1, name: 'petclinic'},
    },
})

describe('the journey chip', () => {

    it.each([
        ['DEPLOYED', 'Deployed'],
        ['SUPERSEDED', 'Superseded'],
        ['IN_PROGRESS', 'In progress'],
        ['ELIGIBLE', 'Eligible'],
        ['NOT_ELIGIBLE', 'Not eligible'],
    ])('says %s as "%s"', (state, label) => {
        render(<JourneyChip entry={entry(state)}/>)
        expect(screen.getByTestId('journey-chip-slot-1')).toHaveTextContent(label)
    })

    it('carries the state as a data attribute', () => {
        render(<JourneyChip entry={entry('DEPLOYED')}/>)
        expect(screen.getByTestId('journey-chip-slot-1')).toHaveAttribute('data-state', 'DEPLOYED')
    })

    it('names the environment and the qualifier', () => {
        render(<JourneyChip entry={entry('ELIGIBLE', {qualifier: 'canary'})}/>)
        expect(screen.getByTestId('journey-chip-slot-1')).toHaveTextContent('production [canary]')
    })

    it('leaves the environment out where the caller does not want it', () => {
        // A decoration beside a build already sits under an environment icon; the strip does not.
        render(<JourneyChip entry={entry('ELIGIBLE')} showSlot={false}/>)
        expect(screen.getByTestId('journey-chip-slot-1')).toHaveTextContent('Eligible')
        expect(screen.getByTestId('journey-chip-slot-1')).not.toHaveTextContent('production')
    })

    it('keeps the refusal off the chip and in the tooltip', () => {
        // A strip is read by scanning. "Not eligible" plus a hover can be scanned; "Not eligible:
        // GOLD promotion is required" is a paragraph per environment.
        render(<JourneyChip entry={entry('NOT_ELIGIBLE', {
            nonEligibleRules: [{id: 'r1', name: 'gold', ruleId: 'promotion', ruleConfig: {promotion: 'GOLD'}}],
        })}/>)
        const chip = screen.getByTestId('journey-chip-slot-1')
        expect(chip).toHaveTextContent('Not eligible')
        expect(chip).not.toHaveTextContent('promotion')
    })

    it('opens on a click', () => {
        const onClick = jest.fn()
        const theEntry = entry('DEPLOYED')
        render(<JourneyChip entry={theEntry} onClick={onClick}/>)
        fireEvent.click(screen.getByTestId('journey-chip-slot-1'))
        expect(onClick).toHaveBeenCalledWith(theEntry.slot)
    })

    it('draws nothing without an entry', () => {
        const {container} = render(<JourneyChip entry={null}/>)
        expect(container).toBeEmptyDOMElement()
    })

    it('survives a state it has never heard of', () => {
        // The server's enum can grow; a chip that crashed on a new value would take a whole strip
        // down with it.
        render(<JourneyChip entry={entry('SOMETHING_NEW')}/>)
        expect(screen.getByTestId('journey-chip-slot-1')).toHaveTextContent('SOMETHING_NEW')
    })
})
