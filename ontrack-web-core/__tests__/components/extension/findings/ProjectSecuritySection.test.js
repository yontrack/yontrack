import "@testing-library/jest-dom"
import {render, screen, within} from "@testing-library/react"
import ProjectSecuritySection from "@components/extension/findings/project/ProjectSecuritySection"

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

jest.mock("../../../../components/services/GraphQL", () => ({
    useQuery: (...args) => mockUseQuery(...args),
}))

const counts = (critical, high, medium, low, unknown) => [
    {severity: 'CRITICAL', count: critical},
    {severity: 'HIGH', count: high},
    {severity: 'MEDIUM', count: medium},
    {severity: 'LOW', count: low},
    {severity: 'UNKNOWN', count: unknown},
]

const summary = {
    open: counts(1, 2, 0, 1, 0),
    openCount: 4,
    acceptedCount: 1,
    resolvedCount: 3,
    scanners: ['trivy'],
    branches: [
        {branch: {id: 10, name: 'main'}, open: counts(1, 1, 0, 0, 0), openCount: 2},
        {branch: {id: 11, name: 'release/1.0'}, open: counts(0, 2, 0, 1, 0), openCount: 3},
        {branch: {id: 12, name: 'feature'}, open: counts(0, 0, 0, 0, 0), openCount: 0},
    ],
}

const project = (authorized = true) => ({
    id: 7,
    name: 'yontrack',
    authorizations: [{name: 'findings', action: 'view', authorized}],
})

const renderSection = ({data = summary, authorized = true} = {}) => {
    mockUseQuery.mockReturnValue({data, loading: false, finished: true, error: null})
    return render(<ProjectSecuritySection project={project(authorized)}/>)
}

beforeEach(() => {
    mockUseQuery.mockReset()
})

describe('Security section of the project page', () => {

    it('is titled Security and links to all the findings', () => {
        renderSection()
        expect(screen.getByText('Security')).toBeInTheDocument()
        expect(screen.getByRole('link', {name: 'All findings'}))
            .toHaveAttribute('href', '/extension/findings/project/7')
    })

    it('counts the open findings by severity, each count linking to them', () => {
        renderSection()
        expect(screen.getByTestId('security-open-CRITICAL')).toHaveTextContent('1')
        expect(screen.getByTestId('security-open-HIGH'))
            .toHaveAttribute('href', '/extension/findings/project/7?severity=HIGH&state=OPEN')
        expect(screen.getByTestId('security-open-HIGH')).toHaveTextContent('2')
        // No link to an empty list
        expect(screen.getByTestId('security-open-MEDIUM')).toHaveTextContent('0')
        expect(screen.getByTestId('security-open-MEDIUM')).not.toHaveAttribute('href')
        expect(screen.getByTestId('security-accepted'))
            .toHaveAttribute('href', '/extension/findings/project/7?state=ACCEPTED')
        expect(screen.getByTestId('security-resolved'))
            .toHaveAttribute('href', '/extension/findings/project/7?state=RESOLVED')
    })

    it('shows the exposure per branch, leaving out the branches with no open finding', () => {
        renderSection()
        const table = screen.getByTestId('security-branches')
        expect(within(table).getByRole('link', {name: 'main'}))
            .toHaveAttribute('href', '/extension/findings/project/7?state=OPEN&branch=main')
        expect(screen.getByTestId('security-branch-release/1.0-HIGH'))
            .toHaveAttribute('href', '/extension/findings/project/7?severity=HIGH&state=OPEN&branch=release%2F1.0')
        expect(screen.getByTestId('security-branch-release/1.0-total')).toHaveTextContent('3')
        expect(within(table).queryByText('feature')).not.toBeInTheDocument()
    })

    it('says when no finding is open on any branch', () => {
        renderSection({
            data: {
                ...summary,
                open: counts(0, 0, 0, 0, 0),
                openCount: 0,
                branches: [{branch: {id: 12, name: 'feature'}, open: counts(0, 0, 0, 0, 0), openCount: 0}],
            }
        })
        expect(screen.getByText('No finding is open on any branch.')).toBeInTheDocument()
        expect(screen.queryByTestId('security-branches')).not.toBeInTheDocument()
    })

    it('says when the project has no finding at all', () => {
        renderSection({
            data: {
                open: counts(0, 0, 0, 0, 0),
                openCount: 0,
                acceptedCount: 0,
                resolvedCount: 0,
                scanners: [],
                branches: [],
            }
        })
        expect(screen.getByText('No security finding has been reported for this project')).toBeInTheDocument()
    })

    it('is hidden from a user who cannot see the findings, and asks nothing of the server', () => {
        const {container} = renderSection({authorized: false})
        expect(container).toBeEmptyDOMElement()
        expect(mockUseQuery).toHaveBeenCalledWith(
            expect.anything(),
            expect.objectContaining({condition: false}),
        )
    })

    it('is hidden when the authorizations say nothing of the findings', () => {
        mockUseQuery.mockReturnValue({data: null, loading: false, finished: false, error: null})
        const {container} = render(<ProjectSecuritySection project={{id: 7, authorizations: []}}/>)
        expect(container).toBeEmptyDOMElement()
    })
})
