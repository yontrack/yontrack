import "@testing-library/jest-dom"
import {fireEvent, render, screen, within} from "@testing-library/react"
import ReadingTile from "@components/extension/scorecard/ReadingTile"

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

const leadTime = {
    key: 'delivery.leadTime',
    day: '2026-09-28',
    computedAt: '2026-09-28T02:00:00',
    windowStart: '2026-06-30T02:00:00',
    windowEnd: '2026-09-28T02:00:00',
    value: 7200,
    basis: 'MEASURED',
    unknownReason: null,
    direction: 'LOWER_IS_BETTER',
    target: 86400,
    targetMet: true,
    details: {
        count: 12,
        p90: 86400,
        mean: 10800,
        min: 60,
        max: 172800,
        markerKind: 'PROMOTION',
        marker: {levels: {main: 'GOLD'}},
        scope: {kind: 'BRANCH_MODEL', branches: ['main', 'release/1.0']},
    },
    history: [
        {day: '2026-09-26', value: 3600},
        {day: '2026-09-27', value: null},
        {day: '2026-09-28', value: 7200},
    ],
}

const details = () => screen.getByTestId('card-details')

describe('A large reading tile, on the scorecard page', () => {

    it('gives the value against its target', () => {
        render(<ReadingTile size="large" reading={leadTime} testId="card"/>)
        expect(screen.getByText('Lead time')).toBeInTheDocument()
        expect(screen.getByTestId('card-value')).toHaveTextContent('2h')
        expect(screen.getByTestId('card-judgement')).toHaveTextContent('Met')
        expect(screen.getByTestId('card-target')).toHaveTextContent('target ≤ 1d')
        expect(within(details()).getByText('Samples')).toBeInTheDocument()
        expect(within(details()).getByText('12')).toBeInTheDocument()
    })

    it('labels the axis of its chart', () => {
        render(<ReadingTile size="large" reading={leadTime} testId="card"/>)
        expect(screen.getByText('90 days ago')).toBeInTheDocument()
        expect(screen.getByText('today')).toBeInTheDocument()
    })

    it('says when it was computed', () => {
        render(<ReadingTile size="large" reading={leadTime} testId="card"/>)
        expect(within(details()).getByText('Computed')).toBeInTheDocument()
    })

    it('says what explains the value', () => {
        render(<ReadingTile size="large" reading={leadTime} testId="card"/>)
        expect(within(details()).getByText('90 days,')).toBeInTheDocument()
        expect(within(details()).getByText('main, release/1.0 (branch model)')).toBeInTheDocument()
        expect(within(details()).getByText('Promotion: GOLD on main')).toBeInTheDocument()
        expect(within(details()).getByText('≤ 1d')).toBeInTheDocument()
        expect(within(details()).getByText('90th percentile')).toBeInTheDocument()
        expect(within(details()).getByText('2d')).toBeInTheDocument()
    })

    it('draws the trend of the daily snapshots', () => {
        render(<ReadingTile size="large" reading={leadTime} testId="card"/>)
        expect(screen.getByTestId('reading-sparkline'))
            .toHaveAttribute('aria-label', 'Lead time, daily, from 1h on 2026-09-26 to 2h on 2026-09-28')
    })

    it('says when there is not enough history for a trend', () => {
        render(<ReadingTile size="large" reading={{...leadTime, history: [{day: '2026-09-28', value: 7200}]}} testId="card"/>)
        expect(screen.getByTestId('reading-sparkline-none')).toBeInTheDocument()
    })

    it('does not give a marker for a test reading, which reads the branches only', () => {
        render(<ReadingTile
                size="large"
            reading={{
                ...leadTime,
                key: 'quality.testPassRate',
                value: null,
                basis: 'UNKNOWN',
                unknownReason: 'NO_TEST_STAMP',
                direction: 'HIGHER_IS_BETTER',
                target: null,
                targetMet: null,
                details: {
                    testStamps: [],
                    markerKind: 'PROMOTION',
                    marker: {levels: {main: 'GOLD'}},
                    scope: {kind: 'ALL_BRANCHES', branches: ['main']},
                },
                history: [],
            }}
            testId="card"
        />)
        expect(within(details()).queryByText('Marker')).not.toBeInTheDocument()
        expect(within(details()).getByText('Test stamps')).toBeInTheDocument()
        expect(within(details()).getByText('None')).toBeInTheDocument()
        expect(screen.getByTestId('card-unknown')).toHaveTextContent('Unknown')
    })

    it('says what the reading measures, up to its marker', () => {
        render(<ReadingTile size="large" reading={leadTime} testId="card"/>)
        expect(screen.getByTestId('card-description')).toHaveTextContent(/first promotion at the marker level/)
    })

    it('says what the reading measures, up to an environment', () => {
        render(<ReadingTile size="large" reading={{...leadTime, details: {...leadTime.details, markerKind: 'ENVIRONMENT'}}} testId="card"/>)
        expect(screen.getByTestId('card-description')).toHaveTextContent(/first successful deployment/)
    })

    it('has no description for a reading out of the catalogue', () => {
        render(<ReadingTile size="large" reading={{...leadTime, key: 'some.other'}} testId="card"/>)
        expect(screen.queryByTestId('card-description')).not.toBeInTheDocument()
    })
})

const judged = (props) => ({...leadTime, ...props})

describe('A reading tile', () => {

    it.each([
        ['MET', judged({}), 'Met'],
        ['MISSED', judged({value: 172800, targetMet: false}), 'Missed'],
        ['SHOWN', judged({target: null, targetMet: null}), 'No target'],
        ['UNKNOWN', judged({value: null, basis: 'UNKNOWN', unknownReason: 'NO_SAMPLES', targetMet: null}), 'Unknown'],
        ['NO_FAILURE', judged({key: 'delivery.mttr', value: null, basis: 'UNKNOWN', unknownReason: 'NO_FAILURE', targetMet: null}), 'No failure in window'],
        ['NO_TARGET', judged({key: 'security.overdue', value: null, target: null, basis: 'UNKNOWN', unknownReason: 'NO_TARGET', targetMet: null}), 'No target set'],
    ])('tags a reading which is %s in words', (judgement, reading, text) => {
        render(<ReadingTile reading={reading} testId="tile"/>)
        const tag = screen.getByTestId('tile-judgement')
        expect(tag).toHaveAttribute('data-judgement', judgement)
        expect(tag).toHaveTextContent(text)
    })

    it('gives the value, large, and the target', () => {
        render(<ReadingTile reading={leadTime} testId="tile"/>)
        expect(screen.getByTestId('tile-value')).toHaveTextContent('2h')
        expect(screen.getByTestId('tile-target')).toHaveTextContent('target ≤ 1d')
    })

    it('says when the set has no target for the reading', () => {
        render(<ReadingTile reading={judged({target: null, targetMet: null})} testId="tile"/>)
        expect(screen.getByTestId('tile-target')).toHaveTextContent('no target in this set')
    })

    it('never reads 0 for no failure in the window', () => {
        render(<ReadingTile reading={judged({key: 'delivery.mttr', value: 0, basis: 'UNKNOWN', unknownReason: 'NO_FAILURE', targetMet: null})} testId="tile"/>)
        expect(screen.getByTestId('tile-value')).not.toHaveTextContent('0')
    })

    it('shows no target set as neutral, with no unknown chip', () => {
        render(<ReadingTile reading={judged({key: 'security.overdue', value: null, target: null, basis: 'UNKNOWN', unknownReason: 'NO_TARGET', targetMet: null})} testId="tile"/>)
        expect(screen.getByTestId('tile-no-target')).toHaveTextContent('No target set')
        expect(screen.queryByTestId('tile-unknown')).toBeNull()
        expect(screen.getByTestId('tile-judgement')).not.toHaveTextContent('Unknown')
    })

    it('gives the reason of an unknown reading', async () => {
        render(<ReadingTile reading={judged({value: null, basis: 'UNKNOWN', unknownReason: 'NO_SAMPLES', targetMet: null})} testId="tile"/>)
        const unknown = screen.getByTestId('tile-unknown')
        expect(unknown).toHaveAttribute('aria-label', 'Unknown: Nothing reached the marker in the window')
        fireEvent.mouseEnter(unknown)
        expect(await screen.findByRole('tooltip')).toHaveTextContent('Nothing reached the marker in the window')
    })

    it('explains the reading in a popover', async () => {
        render(<ReadingTile reading={leadTime} testId="tile"/>)
        fireEvent.mouseEnter(screen.getByRole('button', {name: 'About Lead time'}))
        expect(await screen.findByTestId('scorecard-reading-info-delivery.leadTime')).toHaveTextContent(/first promotion at the marker level/)
    })

    it('shades the zone below the target when lower is better', () => {
        render(<ReadingTile reading={leadTime} testId="tile"/>)
        expect(screen.getByTestId('reading-sparkline')).toHaveAttribute('data-zone', 'below')
    })

    it('shades the zone above the target when higher is better', () => {
        render(<ReadingTile reading={judged({
            key: 'delivery.successRate',
            direction: 'HIGHER_IS_BETTER',
            value: 90,
            target: 80,
            history: [{day: '2026-09-27', value: 85}, {day: '2026-09-28', value: 90}],
        })} testId="tile"/>)
        expect(screen.getByTestId('reading-sparkline')).toHaveAttribute('data-zone', 'above')
    })

    it('shades nothing with no target', () => {
        render(<ReadingTile reading={judged({target: null, targetMet: null})} testId="tile"/>)
        expect(screen.getByTestId('reading-sparkline')).not.toHaveAttribute('data-zone')
    })
})

describe('The help on the rungs of the security maturity, on a reading tile', () => {

    const maturity = judged({
        key: 'security.maturity',
        direction: 'HIGHER_IS_BETTER',
        value: 1,
        target: 2,
        targetMet: false,
        details: {count: 3, expectedKinds: ['CODE'], freshnessDays: 14, markerKind: 'PROMOTION'},
        history: [],
    })

    it('lists the rungs in the popover, the current one and the target one marked', async () => {
        render(<ReadingTile reading={maturity} testId="tile"/>)
        fireEvent.mouseEnter(screen.getByRole('button', {name: 'About Security maturity'}))
        const info = await screen.findByTestId('scorecard-reading-info-security.maturity')
        const rungs = within(info).getAllByRole('listitem')
        expect(rungs).toHaveLength(4)
        expect(rungs[1]).toHaveTextContent('1 · Reported← current')
        expect(rungs[2]).toHaveTextContent('2 · Covered← target')
        expect(rungs[2]).toHaveTextContent('Every expected kind scanned within the last 14 days: Code.')
    })

    it('lists the rungs inline on a large tile', () => {
        render(<ReadingTile size="large" reading={maturity} testId="card"/>)
        const rungs = within(screen.getByTestId('card-description')).getAllByRole('listitem')
        expect(rungs).toHaveLength(4)
        expect(rungs[1]).toHaveTextContent('← current')
    })

    it('explains the rung of its value on focus', async () => {
        render(<ReadingTile reading={maturity} testId="tile"/>)
        const help = within(screen.getByTestId('tile-value')).getByLabelText('1 · Reported: At least one security scan in the window.')
        fireEvent.focus(help)
        expect(await screen.findByRole('tooltip')).toHaveTextContent('At least one security scan in the window.')
    })

    it('marks no current rung for an unknown maturity', async () => {
        render(<ReadingTile reading={{...maturity, value: null, basis: 'UNKNOWN', unknownReason: 'NO_SAMPLES', targetMet: null}} testId="tile"/>)
        fireEvent.mouseEnter(screen.getByRole('button', {name: 'About Security maturity'}))
        const info = await screen.findByTestId('scorecard-reading-info-security.maturity')
        expect(info).not.toHaveTextContent('← current')
    })
})
