import "@testing-library/jest-dom"
import {fireEvent, render, screen} from "@testing-library/react"

// Ant Design reads the responsive breakpoints; jsdom ships no `matchMedia`.
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

import SlotCell from "@components/extension/environments/shared/SlotCell"

/**
 * The cell of the matrix.
 *
 * Its whole job is to say four things in one line without any of them displacing the others, so the
 * tests are about which of them shows when, and about the one that must never be mistaken for
 * another: the in-flight build is not the deployed build.
 */

const build = (name, {release, promotions = []} = {}) => ({
    id: name,
    name,
    releaseProperty: release ? {value: release} : null,
    promotionRuns: promotions.map(level => ({
        id: `run-${level}`,
        promotionLevel: {id: level, name: level, image: false},
    })),
})

const pipeline = (number, status, aBuild, {end = null} = {}) => ({
    id: `p-${number}`,
    number,
    status,
    finished: status === 'DONE' || status === 'CANCELLED',
    start: '2026-09-01T10:00:00Z',
    end,
    build: aBuild,
})

const slot = (overrides = {}) => ({
    id: 'slot-1',
    qualifier: '',
    blocked: false,
    behind: false,
    environment: {id: 'env-1', name: 'production', order: 20},
    project: {id: 1, name: 'petclinic'},
    lastDeployedPipeline: null,
    currentPipeline: null,
    ...overrides,
})

describe('the slot cell', () => {

    it('shows the deployed build and its release', () => {
        render(<SlotCell slot={slot({
            lastDeployedPipeline: pipeline(1, 'DONE', build('104', {release: '1.4.3'}), {end: '2026-09-01T10:00:00Z'}),
        })}/>)
        expect(screen.getByTestId('slot-cell-slot-1-deployed')).toHaveTextContent('104 · 1.4.3')
    })

    it('says so when a slot has never been deployed', () => {
        render(<SlotCell slot={slot()}/>)
        expect(screen.getByTestId('slot-cell-slot-1-never-deployed')).toHaveTextContent('Never deployed')
    })

    it('overlays the in-flight deployment rather than replacing the deployed build', () => {
        // The failure this guards against is a reader taking "107" for what is running in
        // production when 89 is what is running there and 107 is merely trying to be.
        render(<SlotCell slot={slot({
            lastDeployedPipeline: pipeline(2, 'DONE', build('89'), {end: '2026-09-01T10:00:00Z'}),
            currentPipeline: pipeline(3, 'RUNNING', build('107')),
        })}/>)
        expect(screen.getByTestId('slot-cell-slot-1-deployed')).toHaveTextContent('89')
        expect(screen.getByTestId('slot-cell-slot-1-in-flight')).toHaveTextContent('→ 107 running')
    })

    it('calls a candidate a candidate', () => {
        render(<SlotCell slot={slot({currentPipeline: pipeline(3, 'CANDIDATE', build('107'))})}/>)
        expect(screen.getByTestId('slot-cell-slot-1-in-flight')).toHaveTextContent('→ 107 candidate')
    })

    it('shows no overlay for a deployment which has finished', () => {
        render(<SlotCell slot={slot({
            lastDeployedPipeline: pipeline(3, 'DONE', build('107'), {end: '2026-09-01T10:00:00Z'}),
            currentPipeline: pipeline(3, 'DONE', build('107'), {end: '2026-09-01T10:00:00Z'}),
        })}/>)
        expect(screen.queryByTestId('slot-cell-slot-1-in-flight')).not.toBeInTheDocument()
    })

    it('marks a blocked slot', () => {
        render(<SlotCell slot={slot({blocked: true, currentPipeline: pipeline(3, 'CANDIDATE', build('107'))})}/>)
        expect(screen.getByTestId('slot-cell-slot-1-blocked')).toBeInTheDocument()
        expect(screen.getByTestId('slot-cell-slot-1')).toHaveAttribute('data-blocked', 'yes')
    })

    it('marks a blocked slot even when it has never been deployed', () => {
        // The demo's production ← petclinic-ui slot is exactly this: nothing deployed, a candidate
        // held up by a broken rule. A cell that only drew the dot beside a deployed build would
        // lose it.
        render(<SlotCell slot={slot({blocked: true})}/>)
        expect(screen.getByTestId('slot-cell-slot-1-blocked')).toBeInTheDocument()
    })

    it('marks a slot which is behind the one before it', () => {
        render(<SlotCell slot={slot({behind: true})}/>)
        expect(screen.getByTestId('slot-cell-slot-1-behind')).toBeInTheDocument()
        expect(screen.getByTestId('slot-cell-slot-1')).toHaveAttribute('data-behind', 'yes')
    })

    it('shows neither mark on a quiet slot', () => {
        render(<SlotCell slot={slot({
            lastDeployedPipeline: pipeline(1, 'DONE', build('104'), {end: '2026-09-01T10:00:00Z'}),
        })}/>)
        expect(screen.queryByTestId('slot-cell-slot-1-blocked')).not.toBeInTheDocument()
        expect(screen.queryByTestId('slot-cell-slot-1-behind')).not.toBeInTheDocument()
    })

    it('opens on a click', () => {
        const onClick = jest.fn()
        const theSlot = slot()
        render(<SlotCell slot={theSlot} onClick={onClick}/>)
        fireEvent.click(screen.getByTestId('slot-cell-slot-1'))
        expect(onClick).toHaveBeenCalledWith(theSlot)
    })

    it('opens from the keyboard', () => {
        const onClick = jest.fn()
        render(<SlotCell slot={slot()} onClick={onClick}/>)
        fireEvent.keyDown(screen.getByTestId('slot-cell-slot-1'), {key: 'Enter'})
        expect(onClick).toHaveBeenCalled()
    })

    it('is not focusable when it opens nothing', () => {
        render(<SlotCell slot={slot()}/>)
        expect(screen.getByTestId('slot-cell-slot-1')).not.toHaveAttribute('tabindex')
    })

    it('draws nothing at all without a slot', () => {
        // A project with no slot in an environment gets an empty cell, not a dash: "there is no such
        // slot" and "there is a slot and nothing is in it" are different answers.
        const {container} = render(<SlotCell slot={null}/>)
        expect(container).toBeEmptyDOMElement()
    })
})
