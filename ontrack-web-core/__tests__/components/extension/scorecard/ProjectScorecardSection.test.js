import "@testing-library/jest-dom"
import {act, fireEvent, render, screen, waitFor, within} from "@testing-library/react"
import ProjectScorecardSection from "@components/extension/scorecard/project/ProjectScorecardSection"

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
const mockCallGraphQL = jest.fn()

jest.mock("../../../../components/services/GraphQL", () => ({
    useQuery: (...args) => mockUseQuery(...args),
    callGraphQL: (...args) => mockCallGraphQL(...args),
}))

const reading = (props) => ({
    day: '2026-09-28',
    computedAt: '2026-09-28T02:00:00Z',
    windowStart: '2026-06-30T02:00:00Z',
    windowEnd: '2026-09-28T02:00:00Z',
    value: null,
    basis: 'MEASURED',
    unknownReason: null,
    direction: 'LOWER_IS_BETTER',
    target: null,
    targetMet: null,
    details: {},
    ...props,
})

const scorecard = {
    sets: [
        {
            name: 'Project',
            estate: null,
            readings: [
                reading({key: 'delivery.leadTime', value: 7200, details: {count: 12, markerKind: 'PROMOTION'}}),
                reading({key: 'delivery.frequency', value: 3.5, direction: 'HIGHER_IS_BETTER', details: {count: 45, markerKind: 'PROMOTION'}}),
                reading({key: 'delivery.mttr', basis: 'UNKNOWN', unknownReason: 'NO_FAILURE', details: {count: 0, open: 0}}),
                reading({key: 'quality.testPassRate', basis: 'UNKNOWN', unknownReason: 'NO_TEST_STAMP', direction: 'HIGHER_IS_BETTER', details: {testStamps: []}}),
            ],
        },
        {
            name: 'Demo products',
            estate: {
                name: 'Demo products',
                description: 'The products we ship',
                labels: [{id: 1, category: 'portfolio', name: 'product', color: '#00AA00', foregroundColor: '#FFFFFF'}],
                marker: {kind: 'ENVIRONMENT', environment: 'production', qualifier: ''},
            },
            readings: [
                reading({key: 'delivery.leadTime', value: 7200, target: 86400, targetMet: true, details: {count: 12, markerKind: 'ENVIRONMENT'}}),
                reading({key: 'delivery.frequency', value: 3.5, direction: 'HIGHER_IS_BETTER', target: 5, targetMet: false, details: {count: 45}}),
            ],
        },
    ],
}

const project = (config = true) => ({
    id: 7,
    name: 'yontrack',
    authorizations: [{name: 'project', action: 'config', authorized: config}],
})

const renderSection = ({data = scorecard, config = true} = {}) => {
    mockUseQuery.mockReturnValue({data, loading: false, finished: true, error: null})
    return render(<ProjectScorecardSection project={project(config)}/>)
}

beforeEach(() => {
    mockUseQuery.mockReset()
    mockCallGraphQL.mockReset()
})

afterEach(() => {
    jest.useRealTimers()
})

describe('Scorecard section of the project page', () => {

    it('is titled Scorecard and links to the scorecard page', () => {
        renderSection()
        expect(screen.getByText('Scorecard')).toBeInTheDocument()
        expect(screen.getByRole('link', {name: 'Details'}))
            .toHaveAttribute('href', '/extension/scorecard/project/7')
    })

    it('has a column for the project and one per estate, and a row per reading', () => {
        renderSection()
        const table = screen.getByTestId('scorecard-table')
        expect(within(table).getByRole('columnheader', {name: /^Project/})).toBeInTheDocument()
        expect(within(table).getByRole('columnheader', {name: /^Estate: Demo products/})).toBeInTheDocument()
        const rows = table.querySelectorAll('tbody tr.ant-table-row')
        expect(rows).toHaveLength(4)
        expect(rows[0]).toHaveTextContent('Lead time')
        expect(rows[1]).toHaveTextContent('Frequency')
        expect(rows[2]).toHaveTextContent('Time to restore')
        expect(rows[3]).toHaveTextContent('Test pass rate')
    })

    it('gives the value of a reading with its sample count', () => {
        renderSection()
        expect(screen.getByTestId('scorecard-Project-delivery.leadTime-value')).toHaveTextContent('2h')
        expect(screen.getByTestId('scorecard-Project-delivery.leadTime-count')).toHaveTextContent('12 samples')
        expect(screen.getByTestId('scorecard-Project-delivery.frequency-value')).toHaveTextContent('3.5 / week')
    })

    it('judges a reading against the target of its estate, in words', () => {
        renderSection()
        expect(screen.getByTestId('scorecard-Demo products-delivery.leadTime')).toHaveTextContent('Met ≤ 1d')
        expect(screen.getByTestId('scorecard-Demo products-delivery.frequency')).toHaveTextContent('Missed ≥ 5 / week')
        // The set with no estate is never judged
        expect(screen.getByTestId('scorecard-Project-delivery.leadTime')).not.toHaveTextContent('Met')
    })

    it('renders an unknown reading distinctly, with its reason', () => {
        renderSection()
        const unknown = screen.getByTestId('scorecard-Project-quality.testPassRate-unknown')
        expect(unknown).toHaveTextContent('Unknown')
        expect(unknown).toHaveAttribute('aria-label', expect.stringMatching(/^Unknown: No test stamp/))
    })

    it('renders no failure as neutral, never as a value of 0', () => {
        renderSection()
        const cell = screen.getByTestId('scorecard-Project-delivery.mttr')
        expect(cell).toHaveTextContent('No failure in window')
        expect(cell).not.toHaveTextContent('Unknown')
        expect(cell).not.toHaveTextContent('0')
    })

    it('leaves a reading a set does not have empty', () => {
        renderSection()
        expect(screen.queryByTestId('scorecard-Demo products-delivery.mttr')).not.toBeInTheDocument()
    })

    it('says when the readings have not been computed yet', () => {
        renderSection({data: {sets: [{name: 'Project', estate: null, readings: []}]}})
        expect(screen.getByText('The readings of this project have not been computed yet')).toBeInTheDocument()
        expect(screen.queryByTestId('scorecard-table')).not.toBeInTheDocument()
    })

    it('offers the recompute only with the right to configure the project', () => {
        renderSection({config: false})
        expect(screen.queryByTestId('scorecard-recompute')).not.toBeInTheDocument()
    })

    it('queues the recompute and reloads until new readings show up', async () => {
        jest.useFakeTimers()
        mockCallGraphQL.mockResolvedValue({recomputeProjectScorecard: {errors: []}})
        renderSection()
        await act(async () => {
            fireEvent.click(screen.getByTestId('scorecard-recompute'))
        })
        expect(mockCallGraphQL).toHaveBeenCalledWith(expect.objectContaining({
            variables: {projectId: 7},
        }))
        expect(screen.getByTestId('scorecard-recompute')).toHaveTextContent('Recomputing')
        // Reloads the scorecard
        const loads = mockUseQuery.mock.calls.length
        await act(async () => {
            jest.advanceTimersByTime(2000)
        })
        expect(mockUseQuery.mock.calls.length).toBeGreaterThan(loads)
        const lastDeps = mockUseQuery.mock.calls[mockUseQuery.mock.calls.length - 1][1].deps
        expect(lastDeps).toContain(1)
    })

    it('shows the error of a recompute which cannot be queued', async () => {
        mockCallGraphQL.mockResolvedValue({recomputeProjectScorecard: {errors: [{message: 'Not allowed'}]}})
        renderSection()
        await act(async () => {
            fireEvent.click(screen.getByTestId('scorecard-recompute'))
        })
        expect(screen.getByText('Not allowed')).toBeInTheDocument()
        expect(screen.getByTestId('scorecard-recompute')).toHaveTextContent('Recompute')
    })
    it('explains the no-estate column', async () => {
        renderSection()
        fireEvent.mouseEnter(screen.getByRole('button', {name: 'About the Project column'}))
        expect(await screen.findByText(/This project read on its own/)).toBeInTheDocument()
    })

    it('explains an estate column: its description, its marker, and the labels selecting the project', async () => {
        renderSection()
        fireEvent.mouseEnter(screen.getByRole('button', {name: 'About the Estate: Demo products column'}))
        const info = await screen.findByTestId('scorecard-set-info-Demo products')
        expect(info).toHaveTextContent('The products we ship')
        expect(info).toHaveTextContent('Environment: production')
        expect(within(info).getByTestId('label-portfolio:product')).toBeInTheDocument()
    })

    it('opens an explanation on focus, for the keyboard', async () => {
        renderSection()
        fireEvent.focus(screen.getByRole('button', {name: 'About the Project column'}))
        expect(await screen.findByText(/This project read on its own/)).toBeInTheDocument()
    })

    it('explains a reading, up to each kind of marker its sets are read up to', async () => {
        renderSection()
        fireEvent.mouseEnter(screen.getByRole('button', {name: 'About Lead time'}))
        const info = await screen.findByTestId('scorecard-reading-info-delivery.leadTime')
        expect(info).toHaveTextContent('Up to a promotion')
        expect(info).toHaveTextContent(/first promotion at the marker level/)
        expect(info).toHaveTextContent('Up to an environment')
        expect(info).toHaveTextContent(/first successful deployment/)
    })

    it('explains a reading read up to one kind of marker only once', async () => {
        renderSection()
        fireEvent.mouseEnter(screen.getByRole('button', {name: 'About Frequency'}))
        const info = await screen.findByTestId('scorecard-reading-info-delivery.frequency')
        await waitFor(() => expect(info).toHaveTextContent(/Promotions at the marker level/))
        expect(info).not.toHaveTextContent('Up to')
    })

    it('says what the estate columns are', () => {
        renderSection()
        expect(screen.getByTestId('scorecard-legend')).toHaveTextContent(/One column per estate/)
    })

    it('has no legend with no estate column', () => {
        renderSection({data: {sets: [scorecard.sets[0]]}})
        expect(screen.queryByTestId('scorecard-legend')).not.toBeInTheDocument()
    })
})
