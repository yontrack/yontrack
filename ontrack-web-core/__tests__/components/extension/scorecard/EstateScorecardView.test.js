import "@testing-library/jest-dom"
import {fireEvent, render, screen, within} from "@testing-library/react"
import EstateScorecardView from "@components/extension/scorecard/estates/EstateScorecardView"
import {
    gqlEstateFindingsFanOut,
    gqlEstateRankedFindings,
} from "@components/extension/scorecard/estates/EstateFindingsFanOut"

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

const mockOnChange = jest.fn()

const renderView = (projectSets = defaultSets, {tab = 'readings', finding = null} = {}) => {
    const answer = (data) => ({data, loading: false, finished: true, error: null})
    mockUseQuery.mockImplementation((query) => {
        if (query === gqlEstateRankedFindings) {
            return answer([{
                externalId: 'CVE-1',
                title: 'Remote code execution',
                severity: 'CRITICAL',
                openProjects: 1,
                acceptedProjects: 0,
                resolvedProjects: 0,
                firstSeen: '2026-09-01T10:00:00Z',
            }])
        } else if (query === gqlEstateFindingsFanOut) {
            return answer([])
        } else {
            return answer(estateOf(projectSets))
        }
    })
    return render(<EstateScorecardView name="Products" tab={tab} finding={finding} onChange={mockOnChange}/>)
}

beforeEach(() => {
    mockUseQuery.mockReset()
    mockOnChange.mockReset()
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

    it('shows no target set as neutral, not as unknown, and leaves it out of the unknown count', () => {
        renderView([
            {
                project: {id: 7, name: 'alpha'},
                readings: [reading('security.overdue', {basis: 'UNKNOWN', unknownReason: 'NO_TARGET'})],
            },
        ])
        const noTarget = screen.getByTestId('estate-cell-alpha-security.overdue')
        expect(noTarget).toHaveAttribute('data-judgement', 'NO_TARGET')
        expect(noTarget).toHaveTextContent('No target set')
        expect(within(noTarget).queryByText('Unknown')).toBeNull()
        expect(screen.getByTestId('estate-rollup-security.overdue')).toHaveTextContent('0 unknown')
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

    it('has a findings fan-out tab, beside the readings, which it says it opens', () => {
        renderView()
        expect(screen.queryByTestId('estate-fanout')).toBeNull()
        fireEvent.click(screen.getByText('Findings fan-out'))
        expect(mockOnChange).toHaveBeenCalledWith({tab: 'fanout', finding: null})
    })

    it('opens on the tab it is given, with the finding it is given', () => {
        renderView(defaultSets, {tab: 'fanout', finding: 'CVE-2024-38816'})
        expect(screen.getByTestId('estate-fanout')).toBeInTheDocument()
        expect(within(screen.getByTestId('estate-fanout-search')).getByRole('searchbox')).toHaveValue('CVE-2024-38816')
    })

    it('keeps the finding searched when going back to the readings', () => {
        renderView(defaultSets, {tab: 'fanout', finding: 'CVE-2024-38816'})
        fireEvent.click(screen.getByText('Readings'))
        expect(mockOnChange).toHaveBeenCalledWith({tab: 'readings', finding: 'CVE-2024-38816'})
    })

    it('says which finding the fan-out opens', () => {
        renderView(defaultSets, {tab: 'fanout', finding: null})
        fireEvent.click(screen.getByRole('button', {name: 'CVE-1'}))
        expect(mockOnChange).toHaveBeenCalledWith({tab: 'fanout', finding: 'CVE-1'})
    })

    it('says when the estate does not exist', () => {
        mockUseQuery.mockReturnValue({data: null, loading: false, finished: true, error: null})
        render(<EstateScorecardView name="Unknown"/>)
        expect(screen.getByText('No estate is named "Unknown".')).toBeInTheDocument()
    })
})
