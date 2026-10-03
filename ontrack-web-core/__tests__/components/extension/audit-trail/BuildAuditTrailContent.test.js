import "@testing-library/jest-dom"
import {fireEvent, render, screen, within} from "@testing-library/react"
import {BuildAuditTrailContent} from "@components/extension/audit-trail/BuildAuditTrailView"

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

const mockCallGraphQL = jest.fn()

jest.mock("../../../../components/services/GraphQL", () => ({
    useQuery: jest.fn(),
    callGraphQL: (...args) => mockCallGraphQL(...args),
}))

const verification = (overrides = {}) => ({
    chainIntact: true,
    firstBrokenSeq: null,
    endorsementsValid: true,
    firstInvalidEndorsementSeq: null,
    partial: false,
    unendorsedFromSeq: null,
    problems: [],
    missingEvidence: null,
    alteredEvidence: null,
    ...overrides,
})

const actor = {account: 'ci@example.com', via: 'token', tokenName: 'pipeline'}

const auditTrail = (overrides = {}) => ({
    entries: [
        {
            id: 1, seq: 1, type: 'build.created', time: '2026-10-03T08:15:30.120Z', actor,
            payload: {build: {id: 5, project: 'yontrack', branch: 'main', name: '1.0'}},
            prevHash: null, hash: 'h1',
        },
        {
            id: 2, seq: 2, type: 'promotion.added', time: '2026-10-03T08:16:00.000Z', actor,
            payload: {promotionLevel: {id: 3, name: 'GOLD'}, promotionRun: {id: 9}},
            prevHash: 'h1', hash: 'h2',
        },
    ],
    endorsements: [
        {seq: 1, keyId: '0123456789abcdef', time: '2026-10-03T08:15:30.120Z'},
        {seq: 2, keyId: '0123456789abcdef', time: '2026-10-03T08:16:00.000Z'},
    ],
    verification: verification(),
    ...overrides,
})

describe('BuildAuditTrailContent', () => {

    beforeEach(() => mockCallGraphQL.mockReset())

    it('shows an intact trail and its entries', () => {
        render(<BuildAuditTrailContent buildId={5} auditTrail={auditTrail()}/>)
        expect(screen.getByTestId('audit-trail-badge')).toHaveTextContent('Intact')
        expect(screen.queryByTestId('audit-trail-problems')).toBeNull()
        expect(screen.queryByTestId('audit-trail-evidence-badge')).toBeNull()
        expect(screen.getByText('Build 1.0 created')).toBeInTheDocument()
        expect(screen.getByText('Promoted to GOLD')).toBeInTheDocument()
        expect(screen.getAllByText('ci@example.com (token pipeline)')).toHaveLength(2)
    })

    it('shows a broken trail, the checks which failed and the entries failing them', () => {
        render(<BuildAuditTrailContent buildId={5} auditTrail={auditTrail({
            verification: verification({
                chainIntact: false,
                firstBrokenSeq: 2,
                problems: [{seq: 2, type: 'HASH', message: 'The hash of the entry does not match its content.'}],
            }),
        })}/>)
        expect(screen.getByTestId('audit-trail-badge')).toHaveTextContent('Broken at seq 2')
        const problems = screen.getByTestId('audit-trail-problems')
        expect(within(problems).getByText('HASH')).toBeInTheDocument()
        expect(problems).toHaveTextContent('Seq 2 HASH The hash of the entry does not match its content.')
        expect(screen.getAllByLabelText('Fails the verification')).toHaveLength(1)
    })

    it('verifies the trail including its evidence on demand', async () => {
        mockCallGraphQL.mockResolvedValue({
            build: {auditTrail: {verification: verification({missingEvidence: [], alteredEvidence: [2]})}},
        })
        render(<BuildAuditTrailContent buildId={5} auditTrail={auditTrail()}/>)
        fireEvent.click(screen.getByTestId('audit-trail-verify-evidence'))
        expect(await screen.findByTestId('audit-trail-evidence-badge')).toHaveTextContent('Evidence altered at seq 2')
        expect(mockCallGraphQL).toHaveBeenCalledWith(expect.objectContaining({variables: {id: 5}}))
    })

    it('says why the evidence could not be verified', async () => {
        mockCallGraphQL.mockRejectedValue(new Error('The evidence storage cannot be reached.'))
        render(<BuildAuditTrailContent buildId={5} auditTrail={auditTrail()}/>)
        fireEvent.click(screen.getByTestId('audit-trail-verify-evidence'))
        expect(await screen.findByTestId('audit-trail-evidence-error'))
            .toHaveTextContent('The evidence storage cannot be reached.')
        expect(screen.getByTestId('audit-trail-badge')).toHaveTextContent('Intact')
    })
})
