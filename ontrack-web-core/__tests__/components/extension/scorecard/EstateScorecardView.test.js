import "@testing-library/jest-dom"
import {fireEvent, render, screen, within} from "@testing-library/react"
import EstateScorecardView from "@components/extension/scorecard/estates/EstateScorecardView"
import {
    gqlEstateFindingsFanOut,
    gqlEstateRankedFindings,
    gqlEstateSearchedFindings,
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
    security: {expectedKinds: ['DEPENDENCIES', 'CODE'], freshnessDays: 7},
    readingConfigs: [
        {key: 'delivery.leadTime', windowDays: null, target: 86400, direction: 'LOWER_IS_BETTER'},
        {key: 'security.maturity', windowDays: null, target: 2, direction: 'HIGHER_IS_BETTER'},
    ],
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

const renderView = (projectSets = defaultSets, {tab = 'readings', finding = null, search = null} = {}) => {
    const answer = (data) => ({data, loading: false, finished: true, error: null})
    mockUseQuery.mockImplementation((query) => {
        if (query === gqlEstateRankedFindings || query === gqlEstateSearchedFindings) {
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
    return render(<EstateScorecardView name="Products" tab={tab} finding={finding} search={search} onChange={mockOnChange}/>)
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

    // jsdom drops the styles holding a CSS variable: the fill of a cell is named by its data-fill
    it('fills each cell with the colour of its judgement, unknown being a dashed outline with no fill', () => {
        renderView()
        expect(screen.getByTestId('estate-cell-alpha-delivery.leadTime')).toHaveAttribute('data-fill', 'met')
        expect(screen.getByTestId('estate-cell-beta-delivery.leadTime')).toHaveAttribute('data-fill', 'missed')
        expect(screen.getByTestId('estate-cell-alpha-delivery.mttr')).toHaveAttribute('data-fill', 'neutral')
        expect(screen.getByTestId('estate-cell-alpha-quality.testPassRate')).toHaveAttribute('data-fill', 'neutral')
        expect(screen.getByTestId('estate-cell-gamma-delivery.leadTime')).toHaveAttribute('data-fill', 'none')
        const unknown = screen.getByTestId('estate-cell-beta-delivery.mttr')
        expect(unknown).toHaveAttribute('data-fill', 'unknown')
        expect(unknown).toHaveStyle({borderStyle: 'dashed', backgroundColor: 'transparent'})
        expect(unknown.querySelector('svg')).not.toBeNull()
        expect(unknown).toHaveTextContent('Unknown')
    })

    it('gives the project, the reading, its value and its target on the hover of a cell', async () => {
        renderView()
        fireEvent.mouseEnter(screen.getByTestId('estate-cell-beta-delivery.leadTime'))
        const details = await screen.findByTestId('estate-cell-details-beta-delivery.leadTime')
        expect(details).toHaveTextContent('beta')
        expect(details).toHaveTextContent('Lead time: 2d — missed')
        expect(details).toHaveTextContent('Target: ≤ 1d')
    })

    it('gives the details of a judged cell on keyboard focus too', async () => {
        renderView()
        const focusable = screen.getByTestId('estate-cell-alpha-delivery.leadTime').querySelector('[tabindex="0"]')
        fireEvent.focus(focusable)
        const details = await screen.findByTestId('estate-cell-details-alpha-delivery.leadTime')
        expect(details).toHaveTextContent('Lead time: 1h — met')
    })

    it('gives the reason of an unknown reading on the focus of its cell', async () => {
        renderView()
        fireEvent.focus(screen.getByTestId('estate-cell-beta-delivery.mttr'))
        const details = await screen.findByTestId('estate-cell-details-beta-delivery.mttr')
        expect(details).toHaveTextContent('Time to restore: Unknown')
        expect(details).toHaveTextContent('Nothing reached the marker in the window')
        expect(details).toHaveTextContent('No target set by this estate')
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
        expect(screen.getByTestId('estate-rollup-counts-security.overdue')).toHaveAttribute('title', '1 neutral')
    })

    it('shows a reading not computed yet as such', () => {
        renderView()
        const cell = screen.getByTestId('estate-cell-gamma-delivery.leadTime')
        expect(cell).toHaveAttribute('data-judgement', 'NONE')
        expect(within(cell).getByLabelText('Not computed yet')).toBeInTheDocument()
    })

    it('rolls each reading up: its median above a bar of the projects by judgement, their counts under it', () => {
        renderView()
        const leadTime = screen.getByTestId('estate-rollup-delivery.leadTime')
        expect(leadTime).toHaveTextContent('Median 1d')
        expect(within(leadTime).getByRole('img', {name: '1 missed, 1 met, 0 neutral, 0 unknown'})).toBeInTheDocument()
        // The counts in one line: a mark and a number each, the zeros left out, in words on hover
        const leadTimeCounts = within(leadTime).getByTestId('estate-rollup-counts-delivery.leadTime')
        expect(within(leadTimeCounts).getAllByRole('img').map(it => it.getAttribute('aria-label')))
            .toEqual(['1 missed', '1 met'])
        expect(leadTimeCounts).toHaveTextContent(/^11$/)
        expect(leadTimeCounts).toHaveAttribute('title', '1 missed · 1 met')
        const mttr = screen.getByTestId('estate-rollup-delivery.mttr')
        expect(mttr).toHaveTextContent('Median -')
        expect(within(mttr).getByRole('img', {name: '0 missed, 0 met, 1 neutral, 1 unknown'})).toBeInTheDocument()
        const mttrCounts = within(mttr).getByTestId('estate-rollup-counts-delivery.mttr')
        expect(within(mttrCounts).getAllByRole('img').map(it => it.getAttribute('aria-label')))
            .toEqual(['1 neutral', '1 unknown'])
        const frequency = screen.getByTestId('estate-rollup-delivery.frequency')
        expect(within(frequency).getByTestId('estate-rollup-counts-delivery.frequency')).toHaveTextContent(/^Not computed yet$/)
    })

    it('sums each project up: a bar of its readings by judgement, and how many of its judged ones are met', () => {
        renderView()
        const alpha = screen.getByTestId('estate-summary-alpha')
        expect(alpha).toHaveTextContent(/^1 of 1 met$/)
        expect(within(alpha).getByRole('img', {name: '0 missed, 1 met, 2 neutral, 0 unknown'})).toBeInTheDocument()
        const beta = screen.getByTestId('estate-summary-beta')
        expect(beta).toHaveTextContent(/^0 of 1 met$/)
        expect(within(beta).getByRole('img', {name: '1 missed, 0 met, 0 neutral, 1 unknown'})).toBeInTheDocument()
        expect(screen.getByTestId('estate-summary-gamma')).toHaveTextContent(/^None judged$/)
    })

    it('shows the worst projects first: the most missed readings first, then by name', () => {
        renderView()
        const names = () => screen.getAllByTestId(/^estate-project-/).map(it => it.textContent)
        expect(names()).toEqual(['beta', 'alpha', 'gamma'])
        // Default sort, shown on the summary column: a click on it turns it around
        fireEvent.click(column('summary'))
        expect(names()).toEqual(['alpha', 'gamma', 'beta'])
        fireEvent.click(column('summary'))
        expect(names()).toEqual(['beta', 'alpha', 'gamma'])
    })

    it('sorts the projects by name from its header', () => {
        renderView()
        const names = () => screen.getAllByTestId(/^estate-project-/).map(it => it.textContent)
        fireEvent.click(column('project'))
        expect(names()).toEqual(['alpha', 'beta', 'gamma'])
        fireEvent.click(column('project'))
        expect(names()).toEqual(['gamma', 'beta', 'alpha'])
        // Back to the default sort
        fireEvent.click(column('project'))
        expect(names()).toEqual(['beta', 'alpha', 'gamma'])
    })

    it('sorts the projects by a reading from its header', () => {
        renderView()
        const names = () => screen.getAllByTestId(/^estate-project-/).map(it => it.textContent)
        fireEvent.click(column('delivery.leadTime'))
        expect(names()).toEqual(['alpha', 'beta', 'gamma'])
        fireEvent.click(column('delivery.leadTime'))
        expect(names()).toEqual(['beta', 'alpha', 'gamma'])
    })

    it('names each judgement in the legend, with its icon and its swatch', () => {
        renderView()
        const legend = screen.getByTestId('estate-legend')
        ;['met', 'missed', 'neutral', 'unknown', 'none'].forEach(judgement =>
            expect(within(legend).getByTestId(`estate-legend-${judgement}`)).toBeInTheDocument()
        )
        expect(legend).toHaveTextContent('Met')
        expect(legend).toHaveTextContent('Missed')
        expect(legend).toHaveTextContent('Neutral: not judged')
        expect(legend).toHaveTextContent('Unknown')
        expect(within(legend).getByTestId('estate-legend-none')).toHaveTextContent('—Not computed yet')
        expect(legend).toHaveTextContent('latest daily reading of each project in this estate')
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
        expect(mockOnChange).toHaveBeenCalledWith({tab: 'fanout', finding: null, search: null})
    })

    it('opens on the tab it is given, with the finding it is given', () => {
        renderView(defaultSets, {tab: 'fanout', finding: 'CVE-2024-38816'})
        expect(screen.getByTestId('estate-fanout')).toBeInTheDocument()
        expect(within(screen.getByTestId('estate-fanout-search')).getByRole('searchbox')).toHaveValue('CVE-2024-38816')
    })

    it('keeps the finding and the search when going back to the readings', () => {
        renderView(defaultSets, {tab: 'fanout', finding: 'CVE-2024-38816', search: 'cve-2024'})
        fireEvent.click(screen.getByText('Readings'))
        expect(mockOnChange).toHaveBeenCalledWith({tab: 'readings', finding: 'CVE-2024-38816', search: 'cve-2024'})
    })

    it('opens on the search it is given, and says what the fan-out searches', () => {
        renderView(defaultSets, {tab: 'fanout', search: 'cve'})
        expect(within(screen.getByTestId('estate-fanout-search')).getByRole('searchbox')).toHaveValue('cve')
        const input = within(screen.getByTestId('estate-fanout-search')).getByRole('searchbox')
        fireEvent.change(input, {target: {value: 'csrf'}})
        fireEvent.keyDown(input, {key: 'Enter', code: 'Enter', keyCode: 13})
        expect(mockOnChange).toHaveBeenCalledWith({tab: 'fanout', finding: null, search: 'csrf'})
    })

    it('says which finding the fan-out opens', () => {
        renderView(defaultSets, {tab: 'fanout', finding: null})
        fireEvent.click(screen.getByRole('button', {name: 'CVE-1'}))
        expect(mockOnChange).toHaveBeenCalledWith({tab: 'fanout', finding: 'CVE-1', search: null})
    })

    it('says when the estate does not exist', () => {
        mockUseQuery.mockReturnValue({data: null, loading: false, finished: true, error: null})
        render(<EstateScorecardView name="Unknown"/>)
        expect(screen.getByText('No estate is named "Unknown".')).toBeInTheDocument()
    })
})

describe('The help on the scorecard of an estate', () => {

    const securitySets = [
        {
            project: {id: 7, name: 'alpha'},
            readings: [
                reading('security.maturity', {value: 3, direction: 'HIGHER_IS_BETTER', target: 2, targetMet: true}),
            ],
        },
        {
            project: {id: 8, name: 'beta'},
            readings: [
                reading('security.maturity', {value: 2, direction: 'HIGHER_IS_BETTER', target: 2, targetMet: true}),
            ],
        },
    ]

    it('explains a reading and its target from the info of its header', async () => {
        renderView()
        fireEvent.mouseEnter(within(column('delivery.leadTime')).getByRole('button', {name: 'About Lead time'}))
        const info = await screen.findByTestId('estate-column-info-delivery.leadTime')
        expect(info).toHaveTextContent(/first promotion at the marker level/)
        expect(info).toHaveTextContent('Target of this estate: 1d or less — met at or under it, missed above.')
    })

    it('says a reading with no target is shown, not judged', async () => {
        renderView()
        fireEvent.mouseEnter(within(column('quality.testPassRate')).getByRole('button', {name: 'About Test pass rate'}))
        expect(await screen.findByTestId('estate-column-info-quality.testPassRate'))
            .toHaveTextContent('No target set by this estate: values are shown, not judged.')
    })

    it('words a delivery reading for the marker of the estate', async () => {
        mockUseQuery.mockImplementation(() => ({
            data: {
                ...estateOf(defaultSets),
                marker: {kind: 'ENVIRONMENT', levelName: null, environment: 'production', qualifier: null},
            },
            loading: false,
            finished: true,
            error: null,
        }))
        render(<EstateScorecardView name="Products"/>)
        fireEvent.mouseEnter(within(column('delivery.leadTime')).getByRole('button', {name: 'About Lead time'}))
        expect(await screen.findByTestId('estate-column-info-delivery.leadTime')).toHaveTextContent(/successful deployment/)
    })

    it('does not sort when the info of a header is clicked or pressed', () => {
        renderView()
        const names = () => screen.getAllByTestId(/^estate-project-/).map(it => it.textContent)
        const info = within(column('delivery.leadTime')).getByRole('button', {name: 'About Lead time'})
        fireEvent.click(info)
        fireEvent.keyDown(info, {key: 'Enter', code: 'Enter', keyCode: 13})
        fireEvent.click(column('delivery.leadTime'))
        fireEvent.click(column('delivery.leadTime'))
        // Two clicks on the header itself: descending, as if the info had never been clicked
        expect(names()).toEqual(['beta', 'alpha', 'gamma'])
    })

    it('has no info on the project column', () => {
        renderView()
        expect(within(column('project')).queryByRole('button')).toBeNull()
    })

    it('explains the roll-up row', async () => {
        renderView()
        fireEvent.mouseEnter(screen.getByRole('button', {name: 'About All projects'}))
        const info = await screen.findByTestId('estate-rollup-info')
        expect(info).toHaveTextContent(/Median over the projects with a value/)
        expect(info).toHaveTextContent(/missing the target of this estate/)
        expect(info).toHaveTextContent(/Neutral counts the ones not judged, which are not unknown: a value with no target, No failure, No target set/)
        expect(info).toHaveTextContent(/A project not computed yet is counted nowhere/)
    })

    it('lists the rungs of the security maturity in its header, its target marked, covered in the terms of the estate', async () => {
        renderView(securitySets)
        fireEvent.mouseEnter(within(column('security.maturity')).getByRole('button', {name: 'About Security maturity'}))
        const info = await screen.findByTestId('estate-column-info-security.maturity')
        const rungs = within(info).getAllByRole('listitem')
        expect(rungs.map(it => it.getAttribute('data-rung'))).toEqual(['0', '1', '2', '3'])
        expect(rungs[2]).toHaveTextContent('2 · Covered← target')
        expect(rungs[2]).toHaveTextContent('Every expected kind scanned within the last 7 days: Dependencies, Code.')
        expect(rungs[3]).not.toHaveTextContent('← target')
        expect(info).toHaveTextContent('Target of this estate: 2 · Covered or more')
    })

    it('explains the rung of a security maturity cell on focus', async () => {
        renderView(securitySets)
        const help = within(screen.getByTestId('estate-cell-alpha-security.maturity')).getByLabelText(/^3 · Gating: /)
        expect(help).toHaveAttribute('tabindex', '0')
        fireEvent.focus(help)
        expect(await screen.findByRole('tooltip')).toHaveTextContent('A security stamp required by a promotion, or a scan which failed in the window.')
    })

    it('explains the covered rung of a cell in the terms of the estate', () => {
        renderView(securitySets)
        expect(within(screen.getByTestId('estate-cell-beta-security.maturity')).getByLabelText(/^2 · Covered: /))
            .toHaveAttribute('aria-label', '2 · Covered: Every expected kind scanned within the last 7 days: Dependencies, Code.')
    })

    it('explains the rung of the median, but not a median between two rungs', () => {
        renderView([securitySets[0]])
        expect(within(screen.getByTestId('estate-rollup-security.maturity')).getByLabelText(/^3 · Gating: /)).toBeInTheDocument()
        renderView(securitySets)
        const rollUps = screen.getAllByTestId('estate-rollup-security.maturity')
        const between = rollUps[rollUps.length - 1]
        expect(between).toHaveTextContent('Median 2.5')
        expect(within(between).queryByLabelText(/·/)).toBeNull()
    })

    it('has no rung help on another reading', () => {
        renderView()
        expect(within(screen.getByTestId('estate-cell-alpha-delivery.leadTime')).queryByRole('generic', {name: /: /})).toBeNull()
    })
})
