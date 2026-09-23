import "@testing-library/jest-dom"
import {render, screen, within} from "@testing-library/react"
import ValidationRunFindings from "@components/extension/findings/run/ValidationRunFindings"

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

const observation = (id, externalId, severity, extra = {}) => ({
    severity,
    rawSeverity: severity.toLowerCase(),
    installedVersion: '1.0.0',
    fixedVersion: null,
    acceptance: null,
    finding: {
        id,
        externalId,
        title: `Title of ${externalId}`,
        location: 'pkg:maven/org.x/y',
        scanner: 'trivy',
        kind: 'IMAGE',
        state: 'OPEN',
    },
    ...extra,
})

beforeEach(() => {
    mockUseQuery.mockReset()
})

describe('Findings of a validation run', () => {

    it('lists the findings of the scan, linking to their page', () => {
        mockUseQuery.mockReturnValue({
            data: [
                observation(1, 'CVE-A', 'CRITICAL', {fixedVersion: '1.0.1'}),
                observation(2, 'CVE-E', 'HIGH', {
                    acceptance: {effective: true, statement: 'Not reachable', expiresAt: '2026-10-03'},
                }),
            ],
            loading: false,
            finished: true,
        })
        render(<ValidationRunFindings run={{id: 100}}/>)

        expect(mockUseQuery.mock.lastCall[1].variables).toEqual({id: 100})

        const table = screen.getByTestId('validation-run-findings')
        const rows = within(table).getAllByRole('row').slice(1)
        expect(rows).toHaveLength(2)
        expect(within(rows[0]).getByRole('link', {name: 'CVE-A'})).toHaveAttribute('href', '/extension/findings/finding/1')
        expect(within(rows[0]).getByTestId('finding-severity-CRITICAL')).toBeInTheDocument()
        expect(rows[0]).toHaveTextContent('1.0.0')
        expect(rows[0]).toHaveTextContent('1.0.1')
        expect(rows[1]).toHaveTextContent('Accepted until 2026-10-03')
        expect(within(rows[1]).getByText('Accepted until 2026-10-03')).toHaveAttribute('title', 'Not reachable')
    })

    it('says when the scan reported no finding', () => {
        mockUseQuery.mockReturnValue({data: [], loading: false, finished: true})
        render(<ValidationRunFindings run={{id: 100}}/>)
        expect(screen.getByText('This scan reported no finding')).toBeInTheDocument()
    })
})
