import "@testing-library/jest-dom"
import {useState} from "react"
import {fireEvent, render, screen, within} from "@testing-library/react"
import FindingAcrossProjectsView, {
    gqlFindingAcrossProjects,
    gqlSearchedFindings,
} from "@components/extension/findings/FindingAcrossProjectsView"

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

const found = (externalId, props = {}) => ({
    externalId,
    title: `Title of ${externalId}`,
    severity: 'HIGH',
    openProjects: 2,
    acceptedProjects: 1,
    resolvedProjects: 3,
    firstSeen: '2026-09-01T10:00:00Z',
    ...props,
})

const main = {id: 10, name: 'main'}
const release = {id: 11, name: 'release-2.3'}

const exposure = (branch, props = {}) => ({
    branch,
    validationStamp: {id: 1, name: 'scan'},
    since: '2026-09-10T10:00:00Z',
    state: 'EXPOSED',
    counts: true,
    acceptanceExpiresAt: null,
    resolvedAt: null,
    ...props,
})

const finding = {
    id: 1,
    project: {id: 7, name: 'petclinic-billing'},
    externalId: 'CVE-2024-38816',
    location: 'pkg:maven/org.springframework/spring-webmvc',
    scanner: 'trivy',
    kind: 'DEPENDENCIES',
    title: 'Path traversal',
    url: null,
    maxSeverity: 'HIGH',
    state: 'OPEN',
    firstSeen: '2026-09-01T10:00:00Z',
    resolvedAt: null,
    exposures: [
        exposure(main, {state: 'RESOLVED', resolvedAt: '2026-09-20T10:00:00Z'}),
        exposure(release),
    ],
}

const mockQueries = ({searched = [], findings = [finding]} = {}) => {
    mockUseQuery.mockImplementation((query) => {
        if (query === gqlSearchedFindings) {
            return {data: searched, loading: false, finished: true, error: null}
        } else if (query === gqlFindingAcrossProjects) {
            return {data: findings, loading: false, finished: true, error: null}
        }
        throw new Error('Unexpected query')
    })
}

const page = {}

function View({initial}) {
    const [state, setState] = useState(initial)
    page.state = state
    return <FindingAcrossProjectsView {...state} onChange={setState}/>
}

const renderView = ({finding = null, search = null} = {}) => render(<View initial={{finding, search}}/>)

const typeSearch = (text) => {
    const input = within(screen.getByTestId('finding-fanout-search')).getByRole('searchbox')
    fireEvent.change(input, {target: {value: text}})
    fireEvent.keyDown(input, {key: 'Enter', code: 'Enter', keyCode: 13})
}

describe('A finding across all the projects', () => {

    beforeEach(() => {
        mockUseQuery.mockReset()
    })

    it('lands on a search box and an explanation, without any list or query', () => {
        mockQueries()
        renderView()
        expect(screen.getByTestId('finding-fanout-explanation')).toHaveTextContent('Search a finding by its external ID')
        expect(screen.queryByRole('table')).toBeNull()
        expect(mockUseQuery).not.toHaveBeenCalled()
    })

    it('opens the fan-out of the only finding found, without going back to its search', () => {
        mockQueries({searched: [found('CVE-2024-38816')]})
        renderView()
        typeSearch('  cve-2024 ')
        expect(page.state).toEqual({finding: 'CVE-2024-38816', search: null})
        expect(screen.getByTestId('finding-fanout-title')).toHaveTextContent('CVE-2024-38816')
        expect(screen.queryByTestId('finding-fanout-back-search')).toBeNull()
    })

    it('lists the findings found, with their counts, and opens the one picked, keeping the search', () => {
        mockQueries({searched: [found('CVE-2024-1'), found('CVE-2024-2')]})
        renderView()
        typeSearch('cve-2024')
        expect(screen.getByTestId('finding-fanout-searched-findings-caption'))
            .toHaveTextContent('2 findings whose external ID contains "cve-2024", the most widespread first')
        expect(screen.getByTestId('finding-fanout-searched-projects-CVE-2024-1'))
            .toHaveTextContent('Open in 2 · accepted in 1 · resolved in 3')

        fireEvent.click(screen.getByTestId('finding-fanout-searched-finding-CVE-2024-2'))
        expect(page.state).toEqual({finding: 'CVE-2024-2', search: 'cve-2024'})

        fireEvent.click(screen.getByTestId('finding-fanout-back-search'))
        expect(page.state).toEqual({finding: null, search: 'cve-2024'})
    })

    it('asks for the 20 first findings found', () => {
        mockQueries({searched: []})
        renderView({search: 'nothing'})
        const [, options] = mockUseQuery.mock.calls.find(([query]) => query === gqlSearchedFindings)
        expect(options.variables).toEqual({text: 'nothing', size: 20})
        expect(screen.getByText('No finding has an external ID containing "nothing".')).toBeInTheDocument()
    })

    it('shows the projects reporting a finding, without any mention of an estate, its resolved branches last', () => {
        mockQueries()
        renderView({finding: 'CVE-2024-38816'})
        expect(screen.getByTestId('finding-fanout-summary')).toHaveTextContent(
            '1 project reports CVE-2024-38816: exposed in 1, accepted in 0, resolved in 0'
        )
        expect(screen.getByRole('columnheader', {name: 'Branches'})).toBeInTheDocument()
        const branches = within(screen.getByTestId('finding-fanout-branches-petclinic-billing'))
            .getAllByTestId(/^finding-fanout-branch-/)
        expect(branches.map(it => it.getAttribute('data-testid'))).toEqual([
            'finding-fanout-branch-petclinic-billing-release-2.3',
            'finding-fanout-branch-petclinic-billing-main',
        ])
        expect(branches[0]).toHaveTextContent(/since 2026/)
        expect(branches[1]).toHaveTextContent(/Resolved 2026/)
    })

    it('says when no project reports the finding, without counting the hidden ones', () => {
        mockQueries({findings: []})
        renderView({finding: 'CVE-NONE'})
        expect(screen.getByText('No project reports CVE-NONE.')).toBeInTheDocument()
    })

    it('goes back to the landing when the search is cleared', () => {
        mockQueries({findings: [finding]})
        renderView({finding: 'CVE-2024-38816'})
        typeSearch('')
        expect(page.state).toEqual({finding: null, search: null})
        expect(screen.getByTestId('finding-fanout-explanation')).toBeInTheDocument()
    })
})
