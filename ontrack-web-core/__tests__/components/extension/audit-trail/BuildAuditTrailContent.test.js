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
    evidence: [],
    verification: verification(),
    ...overrides,
})

const evidenceEntry = {
    id: 3, seq: 3, type: 'evidence.attached', time: '2026-10-03T08:17:00.000Z', actor,
    payload: {
        validationStamp: {id: 4, name: 'scan'},
        validationRun: {id: 8, order: 1},
        evidence: {id: 21, fileName: 'trivy.json'},
    },
    prevHash: 'h2', hash: 'h3',
}

const evidence = {
    id: 21,
    fileName: 'trivy.json',
    mediaType: 'application/json',
    size: 2048,
    sha256: 'b'.repeat(64),
    collectedAt: '2026-10-03T08:17:00.000',
    collectedBy: actor,
    source: null,
    externalDigest: null,
    deletedAt: null,
    downloadUrl: '/rest/extension/audit-trail/evidence/21/download',
    validationRun: {id: 8, runOrder: 1, validationStamp: {name: 'scan'}},
}

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

    it('shows the evidence of the build between the verification and the entries', () => {
        const trail = auditTrail({evidence: [evidence]})
        trail.entries.push(evidenceEntry)
        render(<BuildAuditTrailContent buildId={5} auditTrail={trail} storageState="OK"/>)
        const verificationSection = screen.getByTestId('audit-trail-verification')
        const evidenceSection = screen.getByTestId('audit-trail-evidence')
        const entriesSection = screen.getByTestId('audit-trail-entries')
        expect(verificationSection.compareDocumentPosition(evidenceSection) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
        expect(evidenceSection.compareDocumentPosition(entriesSection) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
        expect(within(evidenceSection).getByText('Evidence (1)')).toBeInTheDocument()
        expect(within(evidenceSection).getByText('trivy.json')).toBeInTheDocument()
        expect(within(evidenceSection).queryByTestId('evidence-flag')).toBeNull()
    })

    it('flags the evidence the verification of the evidence found altered', async () => {
        mockCallGraphQL.mockResolvedValue({
            build: {auditTrail: {verification: verification({missingEvidence: [], alteredEvidence: [3]})}},
        })
        const trail = auditTrail({evidence: [evidence]})
        trail.entries.push(evidenceEntry)
        render(<BuildAuditTrailContent buildId={5} auditTrail={trail} storageState="OK"/>)
        fireEvent.click(screen.getByTestId('audit-trail-verify-evidence'))
        const evidenceSection = screen.getByTestId('audit-trail-evidence')
        expect(await within(evidenceSection).findByTestId('evidence-flag')).toHaveTextContent('Altered')
    })
})
