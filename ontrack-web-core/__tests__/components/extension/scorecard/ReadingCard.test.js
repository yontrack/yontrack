import "@testing-library/jest-dom"
import {render, screen, within} from "@testing-library/react"
import ReadingCard from "@components/extension/scorecard/project/ReadingCard"

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

describe('A reading on the scorecard page', () => {

    it('gives the value against its target', () => {
        render(<ReadingCard reading={leadTime} testId="card"/>)
        expect(screen.getByText('Lead time')).toBeInTheDocument()
        expect(screen.getByTestId('card-reading')).toHaveTextContent('2h')
        expect(screen.getByTestId('card-reading')).toHaveTextContent('Met ≤ 1d')
        expect(screen.getByTestId('card-reading')).toHaveTextContent('12 samples')
    })

    it('says what explains the value', () => {
        render(<ReadingCard reading={leadTime} testId="card"/>)
        expect(within(details()).getByText('90 days,')).toBeInTheDocument()
        expect(within(details()).getByText('main, release/1.0 (branch model)')).toBeInTheDocument()
        expect(within(details()).getByText('Promotion: GOLD on main')).toBeInTheDocument()
        expect(within(details()).getByText('≤ 1d')).toBeInTheDocument()
        expect(within(details()).getByText('90th percentile')).toBeInTheDocument()
        expect(within(details()).getByText('2d')).toBeInTheDocument()
    })

    it('draws the trend of the daily snapshots', () => {
        render(<ReadingCard reading={leadTime} testId="card"/>)
        expect(screen.getByTestId('reading-sparkline'))
            .toHaveAttribute('aria-label', 'Lead time, daily, from 1h on 2026-09-26 to 2h on 2026-09-28')
    })

    it('says when there is not enough history for a trend', () => {
        render(<ReadingCard reading={{...leadTime, history: [{day: '2026-09-28', value: 7200}]}} testId="card"/>)
        expect(screen.getByTestId('reading-sparkline-none')).toBeInTheDocument()
    })

    it('does not give a marker for a test reading, which reads the branches only', () => {
        render(<ReadingCard
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
        expect(screen.getByTestId('card-reading-unknown')).toHaveTextContent('Unknown')
    })

    it('says what the reading measures, up to its marker', () => {
        render(<ReadingCard reading={leadTime} testId="card"/>)
        expect(screen.getByTestId('card-description')).toHaveTextContent(/first promotion at the marker level/)
    })

    it('says what the reading measures, up to an environment', () => {
        render(<ReadingCard reading={{...leadTime, details: {...leadTime.details, markerKind: 'ENVIRONMENT'}}} testId="card"/>)
        expect(screen.getByTestId('card-description')).toHaveTextContent(/first successful deployment/)
    })

    it('has no description for a reading out of the catalogue', () => {
        render(<ReadingCard reading={{...leadTime, key: 'some.other'}} testId="card"/>)
        expect(screen.queryByTestId('card-description')).not.toBeInTheDocument()
    })
})
