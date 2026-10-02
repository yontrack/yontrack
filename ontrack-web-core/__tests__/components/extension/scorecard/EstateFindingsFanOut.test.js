import "@testing-library/jest-dom"
import {useState} from "react"
import {fireEvent, render, screen, waitFor, within} from "@testing-library/react"
import EstateFindingsFanOut, {
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

const main = {id: 10, name: 'main', disabled: false}
const release = {id: 11, name: 'release', disabled: false}
const spike = {id: 12, name: 'feature-old-spike', disabled: false}
const archived = {id: 13, name: 'archived', disabled: true}

const exposure = (branch, props = {}) => ({
    branch,
    validationStamp: {id: 1, name: 'scan'},
    since: '2026-09-10T10:00:00Z',
    state: 'EXPOSED',
    counts: true,
    accepted: false,
    acceptanceExpiresAt: null,
    resolvedAt: null,
    ...props,
})

const finding = (id, project, props = {}) => ({
    id,
    project,
    externalId: 'CVE-2021-44228',
    location: 'pkg:maven/org.apache.logging.log4j/log4j-core',
    scanner: 'trivy',
    kind: 'DEPENDENCIES',
    title: 'Log4Shell',
    url: 'https://avd.aquasec.com/nvd/cve-2021-44228',
    maxSeverity: 'CRITICAL',
    state: 'OPEN',
    firstSeen: '2026-09-01T10:00:00Z',
    lastSeen: '2026-09-20T10:00:00Z',
    resolvedAt: null,
    exposures: [],
    ...props,
})

const defaultFindings = [
    finding(1, {id: 7, name: 'alpha'}, {
        state: 'RESOLVED',
        resolvedAt: '2026-09-15T10:00:00Z',
        exposures: [exposure(main, {state: 'RESOLVED', resolvedAt: '2026-09-15T10:00:00Z'})],
    }),
    finding(2, {id: 8, name: 'beta'}, {
        exposures: [exposure(main), exposure(release, {state: 'ACCEPTED', accepted: true, acceptanceExpiresAt: '2026-12-31'})],
    }),
]

const ranked = (externalId, props = {}) => ({
    externalId,
    title: `Title of ${externalId}`,
    severity: 'HIGH',
    openProjects: 1,
    acceptedProjects: 0,
    resolvedProjects: 0,
    firstSeen: '2026-09-01T10:00:00Z',
    ...props,
})

const defaultRanked = [
    ranked('CVE-2021-44228', {title: 'Log4Shell', severity: 'CRITICAL', openProjects: 2, acceptedProjects: 1, resolvedProjects: 1}),
    ranked('CVE-2024-38816', {title: 'Path traversal'}),
]

/**
 * `useQuery` answering the ranked findings when nothing is searched, the ones whose external ID
 * contains the text searched, ignoring case, and the findings of the external ID of a fan-out.
 *
 * @param searchedFindings The findings a search finds, whatever the text, instead of the ranked
 * findings containing it
 */
const mockFindings = (findings = defaultFindings, rankedFindings = defaultRanked, searchedFindings = null) => {
    mockUseQuery.mockImplementation((query, {condition, variables}) => {
        if (!condition) {
            return {data: null, loading: false, finished: false, error: null}
        } else if (query === gqlEstateFindingsFanOut) {
            return {data: findings, loading: false, finished: true, error: null}
        } else if (query === gqlEstateRankedFindings) {
            return {data: rankedFindings, loading: false, finished: true, error: null}
        } else if (query === gqlEstateSearchedFindings) {
            const found = searchedFindings ?? rankedFindings.filter(it =>
                it.externalId.toLowerCase().includes(variables.text.toLowerCase())
            )
            return {data: found, loading: false, finished: true, error: null}
        } else {
            throw new Error('Unexpected query')
        }
    })
}

const mockOnChange = jest.fn()

/**
 * The fan-out, its finding and its search kept by its parent, as the estate page keeps them in its
 * URL.
 */
function FanOut({initial}) {
    const [state, setState] = useState(initial)
    const onChange = (value) => {
        mockOnChange(value)
        setState(value)
    }
    return <EstateFindingsFanOut
        estate={{name: 'Products'}}
        finding={state.finding}
        search={state.search}
        onChange={onChange}
    />
}

const renderFanOut = ({finding = null, search = null} = {}) => render(<FanOut initial={{finding, search}}/>)

const search = (text) => {
    const input = within(screen.getByTestId('estate-fanout-search')).getByRole('searchbox')
    fireEvent.change(input, {target: {value: text}})
    fireEvent.keyDown(input, {key: 'Enter', code: 'Enter', keyCode: 13})
}

/**
 * Options of the latest call of a query.
 */
const lastQueryOptions = (query = gqlEstateFindingsFanOut) =>
    mockUseQuery.mock.calls.filter(call => call[0] === query).at(-1)[1]

beforeEach(() => {
    mockUseQuery.mockReset()
    mockOnChange.mockReset()
})

describe('The findings fan-out of an estate', () => {

    it('lists the findings open in the most projects of the estate when nothing is searched', () => {
        mockFindings()
        renderFanOut()
        expect(lastQueryOptions().condition).toBe(false)
        expect(lastQueryOptions(gqlEstateRankedFindings).variables).toEqual({name: 'Products', size: 20})
        expect(screen.queryByTestId('estate-fanout-table')).toBeNull()

        const rows = screen.getAllByTestId(/^estate-ranked-finding-/)
        expect(rows.map(it => it.getAttribute('data-testid'))).toEqual([
            'estate-ranked-finding-CVE-2021-44228',
            'estate-ranked-finding-CVE-2024-38816',
        ])
        const top = rows[0]
        expect(top).toHaveTextContent('CVE-2021-44228')
        expect(top).toHaveTextContent('Log4Shell')
        expect(top).toHaveTextContent('Critical')
        expect(top).toHaveTextContent('Open in 2 · accepted in 1 · resolved in 1')
        expect(screen.getByTestId('estate-ranked-findings-caption'))
            .toHaveTextContent('2 findings open in this estate, the most widespread first')
    })

    it('says when no finding is open in the estate', () => {
        mockFindings(defaultFindings, [])
        renderFanOut()
        expect(screen.getByText('No finding is open in the projects of this estate.')).toBeInTheDocument()
    })

    it('opens the fan-out of a ranked finding from its row', () => {
        mockFindings()
        renderFanOut()
        fireEvent.click(screen.getByRole('button', {name: 'CVE-2021-44228'}))
        expect(mockOnChange).toHaveBeenCalledWith({finding: 'CVE-2021-44228', search: null})
        expect(lastQueryOptions().variables).toEqual({name: 'Products', externalId: 'CVE-2021-44228'})
        expect(screen.getByTestId('estate-fanout-table')).toBeInTheDocument()
        // The search says what is shown
        expect(within(screen.getByTestId('estate-fanout-search')).getByRole('searchbox')).toHaveValue('CVE-2021-44228')
    })

    it('opens the fan-out of a ranked finding from a click anywhere on its row', () => {
        mockFindings()
        renderFanOut()
        fireEvent.click(screen.getByText('Path traversal'))
        expect(mockOnChange).toHaveBeenCalledWith({finding: 'CVE-2024-38816', search: null})
    })

    it('shows the fan-out of the external ID it is given, without any search', () => {
        mockFindings()
        renderFanOut({finding: 'CVE-2021-44228'})
        expect(lastQueryOptions().variables).toEqual({name: 'Products', externalId: 'CVE-2021-44228'})
        // The ranked findings are not even asked for
        expect(mockUseQuery.mock.calls.filter(call => call[0] === gqlEstateRankedFindings)).toHaveLength(0)
        expect(screen.getByTestId('estate-fanout-table')).toBeInTheDocument()
        expect(within(screen.getByTestId('estate-fanout-search')).getByRole('searchbox')).toHaveValue('CVE-2021-44228')
    })

    it('goes back from a fan-out to the ranked findings', () => {
        mockFindings()
        renderFanOut({finding: 'CVE-2021-44228'})
        fireEvent.click(screen.getByRole('button', {name: 'All open findings'}))
        expect(mockOnChange).toHaveBeenCalledWith({finding: null, search: null})
        expect(screen.getAllByTestId(/^estate-ranked-finding-/)).toHaveLength(2)
    })

    it('searches the external IDs containing the text typed, trimmed, among the projects of the estate', () => {
        mockFindings()
        renderFanOut()
        search('  cve-20 ')
        expect(mockOnChange).toHaveBeenCalledWith({finding: null, search: 'cve-20'})
        const options = lastQueryOptions(gqlEstateSearchedFindings)
        expect(options.condition).toBe(true)
        expect(options.variables).toEqual({name: 'Products', text: 'cve-20', size: 20})
        // The search says what is searched
        expect(within(screen.getByTestId('estate-fanout-search')).getByRole('searchbox')).toHaveValue('cve-20')
    })

    it('lists the findings found, as the ranked ones, with what was searched', () => {
        mockFindings()
        renderFanOut()
        search('cve-20')
        expect(screen.queryByTestId('estate-ranked-findings')).toBeNull()
        const rows = screen.getAllByTestId(/^estate-searched-finding-/)
        expect(rows.map(it => it.getAttribute('data-testid'))).toEqual([
            'estate-searched-finding-CVE-2021-44228',
            'estate-searched-finding-CVE-2024-38816',
        ])
        expect(rows[0]).toHaveTextContent('Open in 2 · accepted in 1 · resolved in 1')
        expect(screen.getByTestId('estate-searched-findings-caption'))
            .toHaveTextContent('2 findings whose external ID contains "cve-20", the most widespread first')
    })

    it('says when the findings found are cut, and asks for more of the external ID', () => {
        const many = Array.from({length: 20}, (_, index) => ranked(`CVE-2024-${1000 + index}`))
        mockFindings(defaultFindings, defaultRanked, many)
        renderFanOut()
        search('cve')
        expect(screen.getAllByTestId(/^estate-searched-finding-/)).toHaveLength(20)
        expect(screen.getByTestId('estate-searched-findings-caption'))
            .toHaveTextContent('The first 20 findings whose external ID contains "cve": type more of it to narrow the search')
    })

    it('says when no finding of the estate has an external ID containing the text', () => {
        mockFindings()
        renderFanOut()
        search('GHSA')
        expect(screen.getByText('No finding of this estate has an external ID containing "GHSA".')).toBeInTheDocument()
        expect(screen.queryByTestId('estate-fanout-table')).toBeNull()
    })

    it('opens the fan-out of the only finding found, by its external ID as stored', () => {
        mockFindings()
        renderFanOut()
        search('44228')
        expect(mockOnChange).toHaveBeenLastCalledWith({finding: 'CVE-2021-44228', search: null})
        expect(lastQueryOptions().variables).toEqual({name: 'Products', externalId: 'CVE-2021-44228'})
        expect(screen.getByTestId('estate-fanout-table')).toBeInTheDocument()
        expect(within(screen.getByTestId('estate-fanout-search')).getByRole('searchbox')).toHaveValue('CVE-2021-44228')
    })

    it('opens the fan-out of a finding found from its row, and goes back to the findings found', () => {
        mockFindings()
        renderFanOut()
        search('cve-20')
        fireEvent.click(screen.getByText('Path traversal'))
        expect(mockOnChange).toHaveBeenLastCalledWith({finding: 'CVE-2024-38816', search: 'cve-20'})
        expect(screen.getByTestId('estate-fanout-table')).toBeInTheDocument()

        fireEvent.click(screen.getByRole('button', {name: 'Findings containing "cve-20"'}))
        expect(mockOnChange).toHaveBeenLastCalledWith({finding: null, search: 'cve-20'})
        expect(screen.getAllByTestId(/^estate-searched-finding-/)).toHaveLength(2)

        fireEvent.click(screen.getByRole('button', {name: 'All open findings'}))
        expect(mockOnChange).toHaveBeenLastCalledWith({finding: null, search: null})
        expect(screen.getAllByTestId(/^estate-ranked-finding-/)).toHaveLength(2)
    })

    it('shows the findings found by the search it is given', () => {
        mockFindings()
        renderFanOut({search: 'cve-20'})
        expect(screen.getAllByTestId(/^estate-searched-finding-/)).toHaveLength(2)
        expect(within(screen.getByTestId('estate-fanout-search')).getByRole('searchbox')).toHaveValue('cve-20')
        // Not the ones found by the search
        expect(mockUseQuery.mock.calls.filter(call => call[0] === gqlEstateRankedFindings)).toHaveLength(0)
    })

    it('says what the finding is, and how many projects report it', () => {
        mockFindings()
        renderFanOut()
        search('CVE-2021-44228')
        expect(screen.getByTestId('estate-fanout-title')).toHaveTextContent('CVE-2021-44228Log4Shell')
        expect(screen.getByTestId('estate-fanout-summary')).toHaveTextContent(
            '2 projects of this estate report CVE-2021-44228: exposed in 1, accepted in 0, resolved in 1'
        )
    })

    it('shows the exposed projects first, each with the branches it is exposed on and since when', () => {
        mockFindings()
        renderFanOut()
        search('CVE-2021-44228')
        const projects = screen.getAllByTestId(/^estate-fanout-project-/).map(it => it.textContent)
        expect(projects).toEqual(['beta', 'alpha'])
        expect(screen.getByRole('link', {name: 'beta'})).toHaveAttribute('href', '/project/8')

        const onMain = screen.getByTestId('estate-fanout-branch-beta-main')
        expect(within(onMain).getByRole('link', {name: 'main'})).toHaveAttribute('href', '/branch/10')
        expect(onMain).toHaveTextContent(/since 2026/)
        const onRelease = screen.getByTestId('estate-fanout-branch-beta-release')
        expect(onRelease).toHaveTextContent('accepted until 2026-12-31')

        expect(screen.getByTestId('estate-fanout-state-beta')).toHaveTextContent('Open')
    })

    it('shows a resolved project with no branch, and when the finding was resolved', () => {
        mockFindings()
        renderFanOut()
        search('CVE-2021-44228')
        expect(screen.getByTestId('estate-fanout-state-alpha')).toHaveTextContent('Resolved')
        expect(screen.queryByTestId('estate-fanout-branch-alpha-main')).toBeNull()
        expect(screen.getByTestId('estate-fanout-branches-alpha')).toHaveTextContent(/Resolved 2026/)
    })

    it('links each finding to its page', () => {
        mockFindings()
        renderFanOut()
        search('CVE-2021-44228')
        const links = screen.getAllByRole('link', {name: 'pkg:maven/org.apache.logging.log4j/log4j-core'})
        expect(links.map(it => it.getAttribute('href'))).toEqual([
            '/extension/findings/finding/2',
            '/extension/findings/finding/1',
        ])
    })

    describe('with branches which do not count toward the state of the project', () => {

        const outsideFindings = [
            // Resolved on the branches which count, still exposed on the ones which do not
            finding(3, {id: 9, name: 'gamma'}, {
                state: 'RESOLVED',
                resolvedAt: '2026-09-15T10:00:00Z',
                // By branch name, as the server gives them
                exposures: [
                    exposure(archived, {counts: false}),
                    exposure(spike, {counts: false}),
                    exposure(main, {state: 'RESOLVED', resolvedAt: '2026-09-15T10:00:00Z'}),
                ],
            }),
            finding(4, {id: 10, name: 'delta'}, {
                exposures: [
                    exposure(archived, {counts: false}),
                    exposure(spike, {counts: false}),
                    exposure(release),
                ],
            }),
        ]

        const searchOutside = () => {
            mockFindings(outsideFindings)
            renderFanOut()
            search('CVE-2021-44228')
        }

        it('lists them after the ones which count', () => {
            searchOutside()
            const branches = within(screen.getByTestId('estate-fanout-branches-delta'))
                .getAllByTestId(/^estate-fanout-branch-delta-/)
            expect(branches.map(it => [it.getAttribute('data-testid'), it.getAttribute('data-counts')])).toEqual([
                ['estate-fanout-branch-delta-release', 'true'],
                ['estate-fanout-branch-delta-archived', 'false'],
                ['estate-fanout-branch-delta-feature-old-spike', 'false'],
            ])
        })

        it('says why a branch does not count, to the eye and to a screen reader', async () => {
            searchOutside()
            const outside = screen.getByTestId('estate-fanout-branch-gamma-feature-old-spike')
            expect(outside).toHaveTextContent("Outside the branch model: does not count toward the project's state")
            expect(screen.getByTestId('estate-fanout-branch-gamma-archived'))
                .toHaveTextContent("Disabled branch: does not count toward the project's state")
            expect(screen.getByTestId('estate-fanout-branch-delta-release'))
                .not.toHaveTextContent('does not count')

            fireEvent.focus(within(outside).getByRole('link', {name: 'feature-old-spike'}))
            await waitFor(() => expect(screen.getByRole('tooltip'))
                .toHaveTextContent("Outside the branch model: does not count toward the project's state"))
        })

        it('leaves the state of the project and the summary as the branches which count make them', () => {
            searchOutside()
            expect(screen.getByTestId('estate-fanout-state-gamma')).toHaveTextContent('Resolved')
            expect(screen.getByTestId('estate-fanout-summary')).toHaveTextContent(
                '2 projects of this estate report CVE-2021-44228: exposed in 1, accepted in 0, resolved in 1'
            )
        })
    })

    it('says when no project of the estate reports the external ID', () => {
        mockFindings([])
        renderFanOut({finding: 'CVE-NONE'})
        expect(screen.getByText('No project of this estate reports CVE-NONE.')).toBeInTheDocument()
        expect(screen.queryByTestId('estate-fanout-table')).toBeNull()
    })
})
