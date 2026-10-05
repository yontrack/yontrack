import "@testing-library/jest-dom"
import {fireEvent, render, screen, within} from "@testing-library/react"
import BuildEvidence from "@components/extension/audit-trail/BuildEvidence"

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

const evidence = (id, overrides = {}) => ({
    id,
    fileName: `report-${id}.pdf`,
    mediaType: 'application/pdf',
    size: 1536,
    sha256: `${id}`.repeat(64).substring(0, 64),
    collectedAt: '2026-10-03T08:15:30.120',
    collectedBy: {account: 'ci@example.com', via: 'token', tokenName: 'pipeline'},
    source: null,
    externalDigest: null,
    deletedAt: null,
    downloadUrl: `/rest/extension/audit-trail/evidence/${id}/download`,
    validationRun: {id: 100 + id, runOrder: 1, validationStamp: {name: 'scan'}},
    ...overrides,
})

const noFlags = {verified: false, byId: new Map()}

const renderEvidence = (props = {}) => render(
    <BuildEvidence
        evidence={[evidence(1)]}
        flags={noFlags}
        storageState="OK"
        {...props}
    />
)

describe('BuildEvidence', () => {

    it('says when no validation of the build has evidence', () => {
        renderEvidence({evidence: []})
        expect(screen.getByText('Evidence (0)')).toBeInTheDocument()
        expect(screen.getByText('No evidence attached to any validation of this build')).toBeInTheDocument()
    })

    it('lists the evidence with their validation, linked to its run', () => {
        renderEvidence({
            evidence: [
                evidence(1),
                evidence(2, {fileName: 'tests.json', mediaType: 'application/json', validationRun: {id: 7, runOrder: 2, validationStamp: {name: 'tests'}}}),
            ],
        })
        expect(screen.getByText('Evidence (2)')).toBeInTheDocument()
        const table = screen.getByTestId('build-evidence')
        expect(within(table).getByText('report-1.pdf')).toBeInTheDocument()
        expect(within(table).getByText('tests.json')).toBeInTheDocument()
        expect(within(table).getByRole('link', {name: 'tests #2'})).toHaveAttribute('href', '/validationRun/7')
        expect(within(table).getByRole('link', {name: 'Download report-1.pdf'}))
            .toHaveAttribute('href', '/api/protected/downloads/audit-trail/evidence/1')
        expect(within(table).getByRole('button', {name: 'Preview report-1.pdf'})).toBeInTheDocument()
    })

    it('offers neither an upload nor a deletion', () => {
        renderEvidence()
        expect(screen.queryByText('Upload evidence')).toBeNull()
        expect(screen.queryByRole('button', {name: /^Delete/})).toBeNull()
    })

    it('lists a deleted evidence as deleted, without its download', () => {
        renderEvidence({evidence: [evidence(1, {deletedAt: '2026-10-04T10:00:00', downloadUrl: null})]})
        const table = screen.getByTestId('build-evidence')
        expect(within(table).getByText('Deleted')).toBeInTheDocument()
        expect(within(table).queryByRole('link', {name: /Download/})).toBeNull()
    })

    it('still lists the evidence when the storage cannot be used, without preview nor download', () => {
        renderEvidence({storageState: 'UNREACHABLE'})
        expect(screen.getByTestId('build-evidence-storage')).toHaveTextContent('Evidence storage is unreachable.')
        const table = screen.getByTestId('build-evidence')
        expect(within(table).getByText('report-1.pdf')).toBeInTheDocument()
        expect(within(table).getByText('1'.repeat(64))).toBeInTheDocument()
        expect(within(table).queryByRole('link', {name: /Download/})).toBeNull()
        expect(within(table).queryByRole('button', {name: /Preview/})).toBeNull()
    })

    it('flags the evidence the verification found missing or altered', () => {
        renderEvidence({
            evidence: [evidence(1), evidence(2), evidence(3)],
            flags: {verified: true, byId: new Map([[1, 'missing'], [3, 'altered']])},
        })
        const table = screen.getByTestId('build-evidence')
        expect(within(table).getByText('Missing')).toBeInTheDocument()
        expect(within(table).getByText('Altered')).toBeInTheDocument()
        expect(within(table).getAllByTestId('evidence-flag')).toHaveLength(2)
    })

    it('filters by name, keeping the total in the title and saying how many are shown', () => {
        renderEvidence({
            evidence: [evidence(1), evidence(2, {fileName: 'sbom.json'}), evidence(3)],
        })
        expect(screen.queryByTestId('build-evidence-filtered')).toBeNull()
        const table = screen.getByTestId('build-evidence')
        fireEvent.click(within(table).getByRole('button', {name: 'Filter by name or SHA-256'}))
        fireEvent.change(screen.getByPlaceholderText('Name or SHA-256'), {target: {value: 'SBOM'}})
        fireEvent.click(screen.getByRole('button', {name: /Search/}))
        expect(within(table).getByText('sbom.json')).toBeInTheDocument()
        expect(within(table).queryByText('report-1.pdf')).toBeNull()
        expect(screen.getByText('Evidence (3)')).toBeInTheDocument()
        expect(screen.getByTestId('build-evidence-filtered')).toHaveTextContent('1 of 3 evidence')
    })

    it('filters by validation stamp and by state, failing the verification included once verified', () => {
        renderEvidence({
            evidence: [
                evidence(1),
                evidence(2, {fileName: 'sbom.json', validationRun: {id: 7, runOrder: 1, validationStamp: {name: 'sbom'}}}),
                evidence(3, {fileName: 'old.pdf', deletedAt: '2026-10-04T10:00:00', downloadUrl: null}),
            ],
            flags: {verified: true, byId: new Map([[1, 'altered']])},
        })
        const table = screen.getByTestId('build-evidence')
        const header = (title) => within(table).getAllByRole('columnheader').find(cell => cell.textContent.startsWith(title))
        const pick = (title, option) => {
            fireEvent.click(within(header(title)).getByRole('button'))
            fireEvent.click(screen.getAllByText(option).find(element => element.closest('.ant-dropdown')))
            fireEvent.click(screen.getAllByRole('button', {name: 'OK'}).find(button => button.closest('.ant-dropdown:not(.ant-dropdown-hidden)')))
        }

        pick('Validation', 'sbom')
        expect(within(table).getByText('sbom.json')).toBeInTheDocument()
        expect(within(table).queryByText('report-1.pdf')).toBeNull()
        expect(screen.getByTestId('build-evidence-filtered')).toHaveTextContent('1 of 3 evidence')

        // Then also on the state: no SBOM fails the verification
        pick('State', 'Fails the verification')
        expect(screen.getByText('No evidence matches the filters')).toBeInTheDocument()
        expect(screen.getByTestId('build-evidence-filtered')).toHaveTextContent('0 of 3 evidence')
    })
})
