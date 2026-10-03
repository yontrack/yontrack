import "@testing-library/jest-dom"
import {render, screen} from "@testing-library/react"
import AuditTrailStatusView from "@components/extension/audit-trail/AuditTrailStatusView"

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

const PEM = "-----BEGIN PUBLIC KEY-----\nMCowBQYDK2VwAyEA\n-----END PUBLIC KEY-----\n"

const status = (overrides = {}) => ({
    licensed: true,
    storage: {
        state: 'OK',
        message: null,
        checkedAt: '2026-10-03T10:15:30',
        endpoint: 'http://minio:9000',
        bucket: 'yontrack-audit-trail',
        region: 'us-east-1',
        pathStyle: true,
    },
    keyStatus: 'OK',
    keys: [{keyId: '0123456789abcdef', algorithm: 'Ed25519', publicKey: PEM}],
    ...overrides,
})

const given = (data, {error = null} = {}) => {
    mockUseQuery.mockReturnValue({data, loading: false, finished: true, error})
}

describe('AuditTrailStatusView', () => {

    beforeEach(() => mockUseQuery.mockReset())

    it('shows the licence, the storage and the key when everything is OK', () => {
        given(status())
        render(<AuditTrailStatusView/>)
        expect(screen.getByTestId('audit-trail-licence')).toHaveTextContent('Enabled')
        expect(screen.getByTestId('audit-trail-storage-state')).toHaveTextContent('OK')
        expect(screen.queryByTestId('audit-trail-storage-message')).toBeNull()
        expect(screen.getByTestId('audit-trail-storage-endpoint')).toHaveTextContent('http://minio:9000')
        expect(screen.getByTestId('audit-trail-storage-bucket')).toHaveTextContent('yontrack-audit-trail')
        expect(screen.getByTestId('audit-trail-storage-addressing')).toHaveTextContent('Path-style, region us-east-1')
        expect(screen.getByTestId('audit-trail-key-status')).toHaveTextContent('Provisioned')
        expect(screen.getByTestId('audit-trail-key-id')).toHaveTextContent('0123456789abcdef')
        expect(screen.getByTestId('audit-trail-public-key')).toHaveTextContent('-----BEGIN PUBLIC KEY-----')
    })

    it('shows the licence off', () => {
        given(status({licensed: false}))
        render(<AuditTrailStatusView/>)
        expect(screen.getByTestId('audit-trail-licence')).toHaveTextContent('Disabled')
    })

    it('shows why the storage is unreachable', () => {
        given(status({
            storage: {
                ...status().storage,
                state: 'UNREACHABLE',
                message: 'The bucket yontrack-audit-trail does not exist.',
            }
        }))
        render(<AuditTrailStatusView/>)
        expect(screen.getByTestId('audit-trail-storage-state')).toHaveTextContent('Unreachable')
        expect(screen.getByTestId('audit-trail-storage-message'))
            .toHaveTextContent('The bucket yontrack-audit-trail does not exist.')
    })

    it('shows a storage which is not configured', () => {
        given(status({
            storage: {
                ...status().storage,
                state: 'NOT_CONFIGURED',
                message: 'Missing properties: ontrack.extension.audit-trail.storage.{endpoint}',
                endpoint: null,
                bucket: null,
                pathStyle: false,
            }
        }))
        render(<AuditTrailStatusView/>)
        expect(screen.getByTestId('audit-trail-storage-state')).toHaveTextContent('Not configured')
        expect(screen.getByTestId('audit-trail-storage-endpoint')).toHaveTextContent('Not set')
        expect(screen.getByTestId('audit-trail-storage-bucket')).toHaveTextContent('Not set')
        expect(screen.getByTestId('audit-trail-storage-addressing')).toHaveTextContent('Virtual-hosted, region us-east-1')
    })

    it('shows a key which is not provisioned', () => {
        given(status({keyStatus: 'NOT_PROVISIONED', keys: []}))
        render(<AuditTrailStatusView/>)
        expect(screen.getByTestId('audit-trail-key-status')).toHaveTextContent('Not provisioned')
        expect(screen.getByTestId('audit-trail-key-id')).toHaveTextContent('No key')
        expect(screen.queryByTestId('audit-trail-public-key')).toBeNull()
    })

    it('shows the error of the query', () => {
        given(null, {error: 'Global function GlobalSettings is not granted.'})
        render(<AuditTrailStatusView/>)
        expect(screen.getByText('Global function GlobalSettings is not granted.')).toBeInTheDocument()
    })
})
