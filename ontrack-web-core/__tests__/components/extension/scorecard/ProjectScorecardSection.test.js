import "@testing-library/jest-dom"
import {act, fireEvent, render, screen, waitFor, within} from "@testing-library/react"
import ProjectScorecardSection from "@components/extension/scorecard/project/ProjectScorecardSection"

// antd's grid asks for the media queries of its responsive columns, which jsdom does not answer
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
        {
            name: 'Demo production',
            estate: {
                name: 'Demo production',
                description: null,
                labels: [],
                marker: {kind: 'PROMOTION', levelName: 'GOLD'},
            },
            readings: [
                reading({key: 'delivery.leadTime', value: 7200, target: 3600, targetMet: false, details: {count: 12, markerKind: 'PROMOTION'}}),
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

    it('is titled Scorecard', () => {
        renderSection()
        expect(screen.getByText('Scorecard')).toBeInTheDocument()
    })

    it('asks for the trend of the last 90 days', () => {
        renderSection()
        expect(mockUseQuery.mock.calls[0][1].variables).toEqual({id: 7, days: 90})
    })

    it('has no table any more', () => {
        renderSection()
        expect(screen.queryByRole('table')).not.toBeInTheDocument()
    })

    it('has a card for the project, then one per estate, by name', () => {
        renderSection()
        const cards = [...screen.getByTestId('scorecard-sets').querySelectorAll('[data-selected]')]
        expect(cards.map(it => it.getAttribute('data-testid'))).toEqual([
            'scorecard-set-card-Project',
            'scorecard-set-card-Demo production',
            'scorecard-set-card-Demo products',
        ])
    })

    it('selects the first estate by name by default', () => {
        renderSection()
        expect(screen.getByTestId('scorecard-set-card-Demo production')).toHaveAttribute('data-selected', 'true')
        expect(screen.getByTestId('scorecard-Demo production-delivery.leadTime')).toBeInTheDocument()
        expect(screen.queryByTestId('scorecard-Project-delivery.leadTime')).not.toBeInTheDocument()
    })

    it('selects the Project set for a project in no estate', () => {
        renderSection({data: {sets: [scorecard.sets[0]]}})
        expect(screen.getByTestId('scorecard-set-card-Project')).toHaveAttribute('data-selected', 'true')
        expect(screen.getByTestId('scorecard-Project-delivery.leadTime')).toBeInTheDocument()
    })

    it('shows the readings of the set selected by its card, in the catalogue order', () => {
        renderSection()
        fireEvent.click(within(screen.getByTestId('scorecard-set-card-Project')).getByRole('button', {pressed: false}))
        const tiles = within(screen.getByTestId('scorecard-tiles')).getAllByTestId(/^scorecard-Project-[a-zA-Z.]+$/)
        expect(tiles.map(it => it.getAttribute('data-testid'))).toEqual([
            'scorecard-Project-delivery.leadTime',
            'scorecard-Project-delivery.frequency',
            'scorecard-Project-delivery.mttr',
            'scorecard-Project-quality.testPassRate',
        ])
    })

    it('draws the ring of the targets met in each estate', () => {
        renderSection()
        expect(screen.getByRole('img', {name: '1 of 2 targets met in Demo products'})).toBeInTheDocument()
        expect(screen.getByRole('img', {name: '0 of 1 target met in Demo production'})).toBeInTheDocument()
    })

    it('gives the value of a reading and judges it against the target of its estate, in words', () => {
        renderSection()
        fireEvent.click(within(screen.getByTestId('scorecard-set-card-Demo products')).getByRole('button', {pressed: false}))
        expect(screen.getByTestId('scorecard-Demo products-delivery.leadTime-value')).toHaveTextContent('2h')
        expect(screen.getByTestId('scorecard-Demo products-delivery.leadTime-judgement')).toHaveTextContent('Met')
        expect(screen.getByTestId('scorecard-Demo products-delivery.leadTime-target')).toHaveTextContent('target ≤ 1d')
        expect(screen.getByTestId('scorecard-Demo products-delivery.frequency-judgement')).toHaveTextContent('Missed')
    })

    it('never judges the Project set', () => {
        renderSection({data: {sets: [scorecard.sets[0]]}})
        expect(screen.getByTestId('scorecard-Project-delivery.leadTime-judgement')).toHaveTextContent('No target')
        expect(screen.getByTestId('scorecard-Project-delivery.leadTime-target')).toHaveTextContent('no target in this set')
    })

    it('renders an unknown reading distinctly, with its reason', () => {
        renderSection({data: {sets: [scorecard.sets[0]]}})
        const unknown = screen.getByTestId('scorecard-Project-quality.testPassRate-unknown')
        expect(unknown).toHaveTextContent('Unknown')
        expect(unknown).toHaveAttribute('aria-label', expect.stringMatching(/^Unknown: No test stamp/))
    })

    it('renders no failure as neutral, never as a value of 0', () => {
        renderSection({data: {sets: [scorecard.sets[0]]}})
        const tile = screen.getByTestId('scorecard-Project-delivery.mttr')
        expect(tile).toHaveTextContent('No failure in window')
        expect(tile).not.toHaveTextContent('Unknown')
        expect(screen.getByTestId('scorecard-Project-delivery.mttr-value')).not.toHaveTextContent('0')
    })

    it('links to the scorecard page on the selected set', () => {
        renderSection()
        expect(screen.getByRole('link', {name: 'Details'}))
            .toHaveAttribute('href', '/extension/scorecard/project/7?set=Demo%20production')
        fireEvent.click(within(screen.getByTestId('scorecard-set-card-Project')).getByRole('button', {pressed: false}))
        expect(screen.getByRole('link', {name: 'Details'}))
            .toHaveAttribute('href', '/extension/scorecard/project/7?set=project')
    })

    it('says when the readings were computed, over which window, and what the chart marks are', () => {
        renderSection()
        const legend = screen.getByTestId('scorecard-legend')
        expect(legend).toHaveTextContent(/Computed .* · daily readings over the last 90 days/)
        expect(legend).toHaveTextContent('target met zone')
        expect(legend).toHaveTextContent('target')
    })

    it('says when the readings have not been computed yet', () => {
        renderSection({data: {sets: [{name: 'Project', estate: null, readings: []}]}})
        expect(screen.getByText('The readings of this project have not been computed yet')).toBeInTheDocument()
        expect(screen.queryByTestId('scorecard-sets')).not.toBeInTheDocument()
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

    it('explains the Project set', async () => {
        renderSection()
        fireEvent.mouseEnter(screen.getByRole('button', {name: 'About the Project set'}))
        expect(await screen.findByText(/This project read on its own/)).toBeInTheDocument()
    })

    it('explains an estate: its description, its marker, and the labels selecting the project', async () => {
        renderSection()
        fireEvent.mouseEnter(screen.getByRole('button', {name: 'About the Estate: Demo products set'}))
        const info = await screen.findByTestId('scorecard-set-info-Demo products')
        expect(info).toHaveTextContent('The products we ship')
        expect(info).toHaveTextContent('Environment: production')
        expect(within(info).getByTestId('label-portfolio:product')).toBeInTheDocument()
    })

    it('opens an explanation on focus, for the keyboard', async () => {
        renderSection()
        fireEvent.focus(screen.getByRole('button', {name: 'About the Project set'}))
        expect(await screen.findByText(/This project read on its own/)).toBeInTheDocument()
    })

    it('explains a reading, up to the marker of its set', async () => {
        renderSection()
        fireEvent.click(within(screen.getByTestId('scorecard-set-card-Demo products')).getByRole('button', {pressed: false}))
        fireEvent.mouseEnter(screen.getByRole('button', {name: 'About Lead time'}))
        const info = await screen.findByTestId('scorecard-reading-info-delivery.leadTime')
        await waitFor(() => expect(info).toHaveTextContent(/first successful deployment/))
    })
})
