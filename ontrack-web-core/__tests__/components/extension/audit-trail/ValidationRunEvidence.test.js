import "@testing-library/jest-dom"
import {fireEvent, render, screen, waitFor, within} from "@testing-library/react"
import {ValidationRunEvidenceContent} from "@components/extension/audit-trail/ValidationRunEvidence"
import EvidencePreview from "@components/extension/audit-trail/EvidencePreview"
import EvidenceUploadDialog from "@components/extension/audit-trail/EvidenceUploadDialog"

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

const SHA = 'a'.repeat(64)

const evidence = (overrides = {}) => ({
    id: 1,
    fileName: 'report.pdf',
    mediaType: 'application/pdf',
    size: 1536,
    sha256: SHA,
    collectedAt: '2026-10-03T08:15:30.120',
    collectedBy: {account: 'ci@example.com', via: 'token', tokenName: 'pipeline'},
    source: {tool: 'trivy', version: '0.50.1', url: 'https://ci.example.com/job/1'},
    externalDigest: null,
    deletedAt: null,
    downloadUrl: '/rest/extension/audit-trail/evidence/1/download',
    ...overrides,
})

const run = ({create = false, deleteIt = false} = {}) => ({
    id: 42,
    authorizations: [
        {name: 'evidence', action: 'create', authorized: create},
        {name: 'evidence', action: 'delete', authorized: deleteIt},
    ],
})

const renderContent = (props = {}) => render(
    <ValidationRunEvidenceContent
        run={run()}
        evidence={[evidence()]}
        storageState="OK"
        loading={false}
        onChanged={jest.fn()}
        {...props}
    />
)

describe('ValidationRunEvidenceContent', () => {

    beforeEach(() => mockCallGraphQL.mockReset())

    it('lists the evidence: name, type, size, SHA-256, collected at and by, source', () => {
        renderContent()
        const table = screen.getByTestId('validation-run-evidence')
        expect(within(table).getByText('report.pdf')).toBeInTheDocument()
        expect(within(table).getByText('application/pdf')).toBeInTheDocument()
        expect(within(table).getByText('1.5 KB')).toBeInTheDocument()
        expect(within(table).getByText(SHA)).toBeInTheDocument()
        expect(within(table).getByText('ci@example.com (token pipeline)')).toBeInTheDocument()
        expect(within(table).getByText('trivy 0.50.1')).toBeInTheDocument()
        expect(within(table).getByRole('link', {name: /job/i})).toHaveAttribute('href', 'https://ci.example.com/job/1')
        expect(within(table).getByRole('link', {name: 'Download report.pdf'}))
            .toHaveAttribute('href', '/api/protected/downloads/audit-trail/evidence/1')
    })

    it('never links a source URL which is not HTTP', () => {
        renderContent({evidence: [evidence({source: {tool: 'x', version: null, url: 'javascript:alert(1)'}})]})
        expect(screen.queryByRole('link', {name: /alert/})).toBeNull()
    })

    it.each([
        ['NOT_CONFIGURED', 'Evidence storage is not configured.'],
        ['UNREACHABLE', 'Evidence storage is unreachable.'],
    ])('says the storage is %s in place of the upload and of the list', (state, message) => {
        renderContent({run: run({create: true, deleteIt: true}), storageState: state})
        expect(screen.getByTestId('validation-run-evidence-storage')).toHaveTextContent(message)
        expect(screen.queryByTestId('validation-run-evidence')).toBeNull()
        expect(screen.queryByTestId('validation-run-evidence-upload')).toBeNull()
    })

    it('offers the upload to a user granted evidence/create only', () => {
        const {unmount} = renderContent({run: run({create: true})})
        expect(screen.getByTestId('validation-run-evidence-upload')).toBeInTheDocument()
        unmount()
        renderContent({run: run()})
        expect(screen.queryByTestId('validation-run-evidence-upload')).toBeNull()
    })

    it('offers the deletion to a user granted evidence/delete only', () => {
        const {unmount} = renderContent({run: run({deleteIt: true})})
        expect(screen.getByRole('button', {name: 'Delete report.pdf'})).toBeInTheDocument()
        unmount()
        renderContent({run: run({create: true})})
        expect(screen.queryByRole('button', {name: 'Delete report.pdf'})).toBeNull()
    })

    it('offers the preview for an allow-listed type only', () => {
        renderContent({
            evidence: [
                evidence(),
                evidence({id: 2, fileName: 'report.html', mediaType: 'text/html'}),
            ],
        })
        expect(screen.getByRole('button', {name: 'Preview report.pdf'})).toBeInTheDocument()
        expect(screen.queryByRole('button', {name: 'Preview report.html'})).toBeNull()
        expect(screen.getByRole('link', {name: 'Download report.html'})).toBeInTheDocument()
    })

    it('keeps a deleted evidence listed, marked as deleted, without any action', () => {
        renderContent({
            run: run({create: true, deleteIt: true}),
            evidence: [evidence({deletedAt: '2026-10-03T09:00:00', downloadUrl: null})],
        })
        const table = screen.getByTestId('validation-run-evidence')
        expect(within(table).getByText('report.pdf')).toBeInTheDocument()
        expect(within(table).getByText('Deleted')).toBeInTheDocument()
        expect(screen.queryByRole('button', {name: 'Preview report.pdf'})).toBeNull()
        expect(screen.queryByRole('link', {name: 'Download report.pdf'})).toBeNull()
        expect(screen.queryByRole('button', {name: 'Delete report.pdf'})).toBeNull()
    })

    it('says when the run has no evidence', () => {
        renderContent({evidence: []})
        expect(screen.getByTestId('validation-run-evidence')).toHaveTextContent('No evidence attached to this validation run')
    })

    it('deletes an evidence once confirmed', async () => {
        mockCallGraphQL.mockResolvedValue({deleteEvidence: {errors: null}})
        const onChanged = jest.fn()
        renderContent({run: run({deleteIt: true}), onChanged})
        fireEvent.click(screen.getByRole('button', {name: 'Delete report.pdf'}))
        fireEvent.click(await screen.findByRole('button', {name: 'Delete'}))
        await waitFor(() => expect(onChanged).toHaveBeenCalled())
        expect(mockCallGraphQL).toHaveBeenCalledWith(expect.objectContaining({variables: {id: 1}}))
    })

    it('says why an evidence could not be deleted', async () => {
        mockCallGraphQL.mockResolvedValue({deleteEvidence: {errors: [{message: 'Not allowed.'}]}})
        const onChanged = jest.fn()
        renderContent({run: run({deleteIt: true}), onChanged})
        fireEvent.click(screen.getByRole('button', {name: 'Delete report.pdf'}))
        fireEvent.click(await screen.findByRole('button', {name: 'Delete'}))
        expect(await screen.findByTestId('validation-run-evidence-error')).toHaveTextContent('Not allowed.')
        expect(onChanged).not.toHaveBeenCalled()
    })
})

/**
 * An answer of the download route.
 */
const downloadResponse = ({status = 200, contentType, text = '', blob} = {}) => ({
    ok: status >= 200 && status < 300,
    status,
    headers: {get: (name) => name.toLowerCase() === 'content-type' ? contentType : null},
    text: jest.fn(async () => text),
    blob: jest.fn(async () => blob),
    json: jest.fn(async () => ({error: 'Not Found'})),
    body: {cancel: jest.fn()},
})

describe('EvidencePreview', () => {

    const originalFetch = global.fetch

    beforeEach(() => {
        URL.createObjectURL = jest.fn(() => 'blob:http://localhost/image')
        URL.revokeObjectURL = jest.fn()
    })

    afterEach(() => {
        global.fetch = originalFetch
    })

    const renderPreview = (props = {}) => render(
        <EvidencePreview evidence={evidence()} onClose={jest.fn()} {...props}/>
    )

    it('shows plain text as text, never as markup', async () => {
        global.fetch = jest.fn(async () => downloadResponse({
            contentType: 'text/plain;charset=UTF-8',
            text: '<b>bold?</b> 3 tests passed',
        }))
        renderPreview({evidence: evidence({fileName: 'summary.txt', mediaType: 'text/plain'})})
        const text = await screen.findByTestId('evidence-preview-text')
        expect(text).toHaveTextContent('<b>bold?</b> 3 tests passed')
        expect(text.querySelector('b')).toBeNull()
        expect(global.fetch).toHaveBeenCalledWith('/api/protected/downloads/audit-trail/evidence/1', expect.anything())
    })

    it('shows JSON indented', async () => {
        global.fetch = jest.fn(async () => downloadResponse({contentType: 'application/json', text: '{"a":1}'}))
        renderPreview({evidence: evidence({fileName: 'sbom.json', mediaType: 'application/json'})})
        expect((await screen.findByTestId('evidence-preview-text')).textContent).toBe('{\n  "a": 1\n}')
    })

    it('shows an image', async () => {
        const blob = new Blob(['png'], {type: 'image/png'})
        global.fetch = jest.fn(async () => downloadResponse({contentType: 'image/png', blob}))
        renderPreview({evidence: evidence({fileName: 'screen.png', mediaType: 'image/png'})})
        expect(await screen.findByRole('img', {name: 'screen.png'})).toHaveAttribute('src', 'blob:http://localhost/image')
        expect(URL.createObjectURL).toHaveBeenCalledWith(blob)
    })

    it('frames a PDF from the download route, without reading it twice', async () => {
        const response = downloadResponse({contentType: 'application/pdf'})
        global.fetch = jest.fn(async () => response)
        renderPreview()
        expect(await screen.findByTitle('Preview of report.pdf'))
            .toHaveAttribute('src', '/api/protected/downloads/audit-trail/evidence/1')
        expect(response.body.cancel).toHaveBeenCalled()
    })

    it.each([
        'application/octet-stream',
        'text/html',
        'image/svg+xml',
    ])('previews nothing the server serves as %s', async (contentType) => {
        const response = downloadResponse({contentType, text: '<script>alert(1)</script>'})
        global.fetch = jest.fn(async () => response)
        renderPreview({evidence: evidence({fileName: 'sbom.json', mediaType: 'application/json'})})
        expect(await screen.findByTestId('evidence-preview-refused')).toHaveTextContent('cannot be previewed')
        expect(screen.queryByTestId('evidence-preview-text')).toBeNull()
        expect(response.text).not.toHaveBeenCalled()
        expect(response.body.cancel).toHaveBeenCalled()
        // ... but it can still be downloaded
        expect(screen.getByRole('link', {name: 'Download sbom.json'})).toBeInTheDocument()
    })

    it('says when the evidence could not be read', async () => {
        global.fetch = jest.fn(async () => downloadResponse({status: 404}))
        renderPreview()
        expect(await screen.findByTestId('evidence-preview-error')).toHaveTextContent('Not Found')
    })
})

describe('EvidenceUploadDialog', () => {

    const originalFetch = global.fetch

    afterEach(() => {
        global.fetch = originalFetch
    })

    const renderDialog = (props = {}) => render(
        <EvidenceUploadDialog run={run({create: true})} open={true} onClose={jest.fn()} onUploaded={jest.fn()} {...props}/>
    )

    const chooseFile = (name = 'summary.txt') => {
        const file = new File(['3 tests passed'], name, {type: 'text/plain'})
        // antd's Upload hides its file input behind its button
        fireEvent.change(document.querySelector('input[type="file"]'), {target: {files: [file]}})
        return file
    }

    it('uploads the file and its source to the validation run', async () => {
        global.fetch = jest.fn(async () => ({
            ok: true,
            status: 201,
            json: async () => ({id: 7}),
        }))
        const onUploaded = jest.fn()
        renderDialog({onUploaded})
        chooseFile()
        expect(await screen.findByText('summary.txt')).toBeInTheDocument()
        fireEvent.change(screen.getByLabelText('Source tool'), {target: {value: 'pytest'}})
        fireEvent.click(screen.getByRole('button', {name: 'Upload'}))

        await waitFor(() => expect(onUploaded).toHaveBeenCalled())
        const [url, init] = global.fetch.mock.calls[0]
        expect(url).toBe('/api/protected/uploads/audit-trail/validation-runs/42/evidence')
        expect(init.method).toBe('POST')
        expect(init.body.get('file').name).toBe('summary.txt')
        expect(init.body.get('sourceTool')).toBe('pytest')
        expect(init.body.has('sourceVersion')).toBe(false)
    })

    it('says why the evidence was refused', async () => {
        global.fetch = jest.fn(async () => ({
            ok: false,
            status: 413,
            json: async () => ({
                status: 413,
                code: 'audit-trail.evidence.too-large',
                message: 'The evidence is bigger than the instance allows.',
            }),
        }))
        const onUploaded = jest.fn()
        renderDialog({onUploaded})
        chooseFile()
        await screen.findByText('summary.txt')
        fireEvent.click(screen.getByRole('button', {name: 'Upload'}))

        expect(await screen.findByTestId('evidence-upload-error'))
            .toHaveTextContent('The evidence is bigger than the instance allows.')
        expect(onUploaded).not.toHaveBeenCalled()
    })

    it('asks for a file', async () => {
        global.fetch = jest.fn()
        renderDialog()
        fireEvent.click(screen.getByRole('button', {name: 'Upload'}))
        expect(await screen.findByText('A file is required.')).toBeInTheDocument()
        expect(global.fetch).not.toHaveBeenCalled()
    })
})
