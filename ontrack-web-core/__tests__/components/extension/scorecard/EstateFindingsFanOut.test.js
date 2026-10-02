import "@testing-library/jest-dom"
import {fireEvent, render, screen, waitFor, within} from "@testing-library/react"
import EstateFindingsFanOut from "@components/extension/scorecard/estates/EstateFindingsFanOut"

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

/**
 * `useQuery` answering the findings once a search has been made.
 */
const mockFindings = (findings = defaultFindings) => {
    mockUseQuery.mockImplementation((_query, {condition}) =>
        condition ?
            {data: findings, loading: false, finished: true, error: null} :
            {data: null, loading: false, finished: false, error: null}
    )
}

const search = (text) => {
    const input = within(screen.getByTestId('estate-fanout-search')).getByRole('searchbox')
    fireEvent.change(input, {target: {value: text}})
    fireEvent.keyDown(input, {key: 'Enter', code: 'Enter', keyCode: 13})
}

const lastQueryOptions = () => mockUseQuery.mock.calls[mockUseQuery.mock.calls.length - 1][1]

beforeEach(() => {
    mockUseQuery.mockReset()
})

describe('The findings fan-out of an estate', () => {

    it('asks for an external ID before querying anything', () => {
        mockFindings()
        render(<EstateFindingsFanOut estate={{name: 'Products'}}/>)
        expect(screen.getByText(/Search a finding by its external ID/)).toBeInTheDocument()
        expect(lastQueryOptions().condition).toBe(false)
        expect(screen.queryByTestId('estate-fanout-table')).toBeNull()
    })

    it('looks for the external ID typed, trimmed, among the projects of the estate', () => {
        mockFindings()
        render(<EstateFindingsFanOut estate={{name: 'Products'}}/>)
        search('  CVE-2021-44228 ')
        const options = lastQueryOptions()
        expect(options.condition).toBe(true)
        expect(options.variables).toEqual({name: 'Products', externalId: 'CVE-2021-44228'})
    })

    it('says what the finding is, and how many projects report it', () => {
        mockFindings()
        render(<EstateFindingsFanOut estate={{name: 'Products'}}/>)
        search('CVE-2021-44228')
        expect(screen.getByTestId('estate-fanout-title')).toHaveTextContent('CVE-2021-44228Log4Shell')
        expect(screen.getByTestId('estate-fanout-summary')).toHaveTextContent(
            '2 projects of this estate report CVE-2021-44228: exposed in 1, accepted in 0, resolved in 1'
        )
    })

    it('shows the exposed projects first, each with the branches it is exposed on and since when', () => {
        mockFindings()
        render(<EstateFindingsFanOut estate={{name: 'Products'}}/>)
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
        render(<EstateFindingsFanOut estate={{name: 'Products'}}/>)
        search('CVE-2021-44228')
        expect(screen.getByTestId('estate-fanout-state-alpha')).toHaveTextContent('Resolved')
        expect(screen.queryByTestId('estate-fanout-branch-alpha-main')).toBeNull()
        expect(screen.getByTestId('estate-fanout-branches-alpha')).toHaveTextContent(/Resolved 2026/)
    })

    it('links each finding to its page', () => {
        mockFindings()
        render(<EstateFindingsFanOut estate={{name: 'Products'}}/>)
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
            render(<EstateFindingsFanOut estate={{name: 'Products'}}/>)
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
        render(<EstateFindingsFanOut estate={{name: 'Products'}}/>)
        search('CVE-NONE')
        expect(screen.getByText('No project of this estate reports CVE-NONE.')).toBeInTheDocument()
        expect(screen.queryByTestId('estate-fanout-table')).toBeNull()
    })
})
