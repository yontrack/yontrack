import "@testing-library/jest-dom"
import {fireEvent, render, screen, within} from "@testing-library/react"
import EstateScorecardView from "@components/extension/scorecard/estates/EstateScorecardView"

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

jest.mock("../../../../components/services/GraphQL", () => ({
    useQuery: (...args) => mockUseQuery(...args),
    callGraphQL: jest.fn(),
}))

const reading = (key, props = {}) => ({
    key,
    computedAt: '2026-09-28T02:00:00Z',
    value: null,
    basis: 'MEASURED',
    unknownReason: null,
    direction: 'LOWER_IS_BETTER',
    target: null,
    targetMet: null,
    ...props,
})

const estateOf = (projectSets) => ({
    id: 1,
    name: 'Products',
    description: 'Our products',
    labels: [],
    marker: {kind: 'PROMOTION', levelName: 'GOLD', environment: null, qualifier: null},
    readingConfigs: [{key: 'delivery.leadTime', windowDays: null, target: 86400, direction: 'LOWER_IS_BETTER'}],
    projectSets,
})

const defaultSets = [
    {
        project: {id: 7, name: 'alpha'},
        readings: [
            reading('delivery.leadTime', {value: 3600, target: 86400, targetMet: true}),
            reading('delivery.mttr', {basis: 'UNKNOWN', unknownReason: 'NO_FAILURE'}),
            reading('quality.testPassRate', {value: 92, direction: 'HIGHER_IS_BETTER'}),
        ],
    },
    {
        project: {id: 8, name: 'beta'},
        readings: [
            reading('delivery.leadTime', {value: 172800, target: 86400, targetMet: false}),
            reading('delivery.mttr', {basis: 'UNKNOWN', unknownReason: 'NO_SAMPLES'}),
        ],
    },
    {
        project: {id: 9, name: 'gamma'},
        readings: [],
    },
]

// The sticky header of the table is rendered apart from its body: the header cells are found once
// per header rendered, the first one being the visible one
const column = (key) => screen.getAllByTestId(`estate-column-${key}`)[0]

const renderView = (projectSets = defaultSets) => {
    mockUseQuery.mockReturnValue({data: estateOf(projectSets), loading: false, finished: true, error: null})
    return render(<EstateScorecardView name="Products"/>)
}

beforeEach(() => {
    mockUseQuery.mockReset()
})

describe('The scorecard of an estate', () => {

    it('shows one row per project, linking to its scorecard on the set of the estate', () => {
        renderView()
        expect(screen.getByRole('link', {name: 'alpha'})).toHaveAttribute('href', '/extension/scorecard/project/7?set=Products')
        expect(screen.getByRole('link', {name: 'beta'})).toHaveAttribute('href', '/extension/scorecard/project/8?set=Products')
        expect(screen.getByRole('link', {name: 'gamma'})).toHaveAttribute('href', '/extension/scorecard/project/9?set=Products')
    })

    it('gives the target of the estate in the header of its reading', () => {
        renderView()
        expect(column('delivery.leadTime')).toHaveTextContent('Lead time≤ 1d')
    })

    it('judges the readings against their target, in words and not by colour alone', () => {
        renderView()
        const met = screen.getByTestId('estate-cell-alpha-delivery.leadTime')
        expect(met).toHaveAttribute('data-judgement', 'MET')
        expect(met).toHaveTextContent('1h')
        expect(within(met).getByLabelText('Met')).toBeInTheDocument()
        const missed = screen.getByTestId('estate-cell-beta-delivery.leadTime')
        expect(missed).toHaveAttribute('data-judgement', 'MISSED')
        expect(missed).toHaveTextContent('2d')
        expect(within(missed).getByLabelText('Missed')).toBeInTheDocument()
        const shown = screen.getByTestId('estate-cell-alpha-quality.testPassRate')
        expect(shown).toHaveAttribute('data-judgement', 'SHOWN')
        expect(shown).toHaveTextContent('92%')
    })

    it('shows an unknown reading apart from no failure in the window, which is neutral', () => {
        renderView()
        const unknown = screen.getByTestId('estate-cell-beta-delivery.mttr')
        expect(unknown).toHaveAttribute('data-judgement', 'UNKNOWN')
        expect(within(unknown).getByLabelText('Unknown: Nothing reached the marker in the window')).toBeInTheDocument()
        const noFailure = screen.getByTestId('estate-cell-alpha-delivery.mttr')
        expect(noFailure).toHaveAttribute('data-judgement', 'NO_FAILURE')
        expect(noFailure).toHaveTextContent('No failure')
        expect(within(noFailure).queryByText('Unknown')).toBeNull()
    })

    it('shows a reading not computed yet as such', () => {
        renderView()
        const cell = screen.getByTestId('estate-cell-gamma-delivery.leadTime')
        expect(cell).toHaveAttribute('data-judgement', 'NONE')
        expect(within(cell).getByLabelText('Not computed yet')).toBeInTheDocument()
    })

    it('rolls each reading up: median, unknown count, missed count', () => {
        renderView()
        const leadTime = screen.getByTestId('estate-rollup-delivery.leadTime')
        expect(leadTime).toHaveTextContent('Median 1d')
        expect(leadTime).toHaveTextContent('1 missed')
        expect(leadTime).toHaveTextContent('0 unknown')
        const mttr = screen.getByTestId('estate-rollup-delivery.mttr')
        expect(mttr).toHaveTextContent('Median -')
        expect(mttr).toHaveTextContent('1 unknown')
        expect(mttr).toHaveTextContent('0 missed')
    })

    it('sorts the projects by a reading from its header', () => {
        renderView()
        const names = () => screen.getAllByTestId(/^estate-project-/).map(it => it.textContent)
        expect(names()).toEqual(['alpha', 'beta', 'gamma'])
        fireEvent.click(column('delivery.leadTime'))
        expect(names()).toEqual(['alpha', 'beta', 'gamma'])
        fireEvent.click(column('delivery.leadTime'))
        expect(names()).toEqual(['beta', 'alpha', 'gamma'])
    })

    it('has no measured-only toggle while no reading is estimated', () => {
        renderView()
        expect(screen.queryByTestId('estate-measured-only')).toBeNull()
    })

    it('has a measured-only toggle once a reading is estimated, which leaves the estimated readings out', () => {
        renderView([
            {
                project: {id: 7, name: 'alpha'},
                readings: [reading('delivery.leadTime', {value: 3600, basis: 'ESTIMATED'})],
            },
        ])
        expect(screen.getByTestId('estate-cell-alpha-delivery.leadTime')).toHaveTextContent('1h')
        fireEvent.click(screen.getByTestId('estate-measured-only'))
        expect(screen.getByTestId('estate-cell-alpha-delivery.leadTime')).toHaveAttribute('data-judgement', 'NONE')
    })

    it('says when the estate does not exist', () => {
        mockUseQuery.mockReturnValue({data: null, loading: false, finished: true, error: null})
        render(<EstateScorecardView name="Unknown"/>)
        expect(screen.getByText('No estate is named "Unknown".')).toBeInTheDocument()
    })
})
