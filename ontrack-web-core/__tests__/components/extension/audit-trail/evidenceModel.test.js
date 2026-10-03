import {
    canDeleteEvidence,
    canUploadEvidence,
    evidenceSize,
    evidenceSourceUrl,
    evidenceStorageMessage,
    isPreviewOffered,
    PREVIEW_MAX_SIZE,
    previewKind,
    previewText,
    showEvidenceCell,
    uploadErrorText,
} from "@components/extension/audit-trail/evidenceModel"

const authorizations = ({create = false, deleteIt = false} = {}) => [
    {name: 'evidence', action: 'create', authorized: create},
    {name: 'evidence', action: 'delete', authorized: deleteIt},
    {name: 'validation_run', action: 'status_change', authorized: true},
]

const evidence = (overrides = {}) => ({
    id: 1,
    fileName: 'report.pdf',
    mediaType: 'application/pdf',
    size: 1024,
    deletedAt: null,
    downloadUrl: '/rest/extension/audit-trail/evidence/1/download',
    ...overrides,
})

describe('showEvidenceCell', () => {

    it('is shown when the run has evidence, even to a user who cannot upload', () => {
        expect(showEvidenceCell({evidence: [{id: 1}], authorizations: authorizations()})).toBe(true)
    })

    it('is shown to a user who may upload, even when the run has no evidence', () => {
        expect(showEvidenceCell({evidence: [], authorizations: authorizations({create: true})})).toBe(true)
    })

    it('is hidden when the run has no evidence and the user may not upload', () => {
        expect(showEvidenceCell({evidence: [], authorizations: authorizations()})).toBe(false)
    })

    it('is hidden while the run is not loaded', () => {
        expect(showEvidenceCell({})).toBe(false)
    })
})

describe('permissions', () => {

    it('upload follows evidence/create', () => {
        expect(canUploadEvidence({authorizations: authorizations({create: true})})).toBe(true)
        expect(canUploadEvidence({authorizations: authorizations({deleteIt: true})})).toBe(false)
    })

    it('deletion follows evidence/delete', () => {
        expect(canDeleteEvidence({authorizations: authorizations({deleteIt: true})})).toBe(true)
        expect(canDeleteEvidence({authorizations: authorizations({create: true})})).toBe(false)
    })
})

describe('evidenceStorageMessage', () => {

    it('says nothing when the storage is OK', () => {
        expect(evidenceStorageMessage('OK')).toBeNull()
    })

    it('says nothing while the state is not known', () => {
        expect(evidenceStorageMessage(undefined)).toBeNull()
    })

    it('says when the storage is not configured', () => {
        expect(evidenceStorageMessage('NOT_CONFIGURED')).toBe('Evidence storage is not configured.')
    })

    it('says when the storage cannot be reached', () => {
        expect(evidenceStorageMessage('UNREACHABLE')).toBe('Evidence storage is unreachable.')
    })
})

describe('previewKind', () => {

    it.each([
        ['application/pdf', 'pdf'],
        ['image/png', 'image'],
        ['image/jpeg', 'image'],
        ['image/gif', 'image'],
        ['image/webp', 'image'],
        ['application/json', 'json'],
        ['application/vnd.cyclonedx+json', 'json'],
        ['text/plain', 'text'],
        ['text/plain;charset=UTF-8', 'text'],
        ['Application/JSON; charset=utf-8', 'json'],
    ])('previews %s as %s', (contentType, kind) => {
        expect(previewKind(contentType)).toBe(kind)
    })

    it.each([
        'application/octet-stream',
        'text/html',
        'text/html; charset=utf-8',
        'image/svg+xml',
        'application/xml',
        'application/javascript',
        'text/javascript',
        '',
        null,
        undefined,
    ])('never previews %s', (contentType) => {
        expect(previewKind(contentType)).toBeNull()
    })
})

describe('isPreviewOffered', () => {

    it('is offered for an allow-listed type', () => {
        expect(isPreviewOffered(evidence())).toBe(true)
    })

    it('is not offered for a type which is not allow-listed', () => {
        expect(isPreviewOffered(evidence({fileName: 'report.html', mediaType: 'text/html'}))).toBe(false)
        expect(isPreviewOffered(evidence({fileName: 'logo.svg', mediaType: 'image/svg+xml'}))).toBe(false)
    })

    it('is not offered for a deleted evidence', () => {
        expect(isPreviewOffered(evidence({deletedAt: '2026-10-03T10:00:00', downloadUrl: null}))).toBe(false)
    })

    it('is not offered beyond the size of a preview', () => {
        expect(isPreviewOffered(evidence({size: PREVIEW_MAX_SIZE}))).toBe(true)
        expect(isPreviewOffered(evidence({size: PREVIEW_MAX_SIZE + 1}))).toBe(false)
    })
})

describe('previewText', () => {

    it('indents JSON', () => {
        expect(previewText('{"a":1,"b":[true]}', 'json')).toEqual({
            text: '{\n  "a": 1,\n  "b": [\n    true\n  ]\n}',
            truncated: false,
        })
    })

    it('keeps JSON which does not parse as it is', () => {
        expect(previewText('{"a":', 'json')).toEqual({text: '{"a":', truncated: false})
    })

    it('keeps text as it is', () => {
        expect(previewText('<b>not markup</b>', 'text')).toEqual({text: '<b>not markup</b>', truncated: false})
    })

    it('truncates a long text', () => {
        const {text, truncated} = previewText('x'.repeat(300 * 1024), 'text')
        expect(text).toHaveLength(256 * 1024)
        expect(truncated).toBe(true)
    })
})

describe('evidenceSize', () => {

    it.each([
        [0, '0 bytes'],
        [1, '1 byte'],
        [1023, '1023 bytes'],
        [1024, '1.0 KB'],
        [1536, '1.5 KB'],
        [5 * 1024 * 1024, '5.0 MB'],
        [3 * 1024 * 1024 * 1024, '3.0 GB'],
    ])('%d reads %s', (size, text) => {
        expect(evidenceSize(size)).toBe(text)
    })
})

describe('evidenceSourceUrl', () => {

    it('keeps an HTTP or HTTPS URL', () => {
        expect(evidenceSourceUrl('https://ci.example.com/job/1')).toBe('https://ci.example.com/job/1')
        expect(evidenceSourceUrl('http://ci.example.com/job/1')).toBe('http://ci.example.com/job/1')
    })

    it('drops any other URL', () => {
        expect(evidenceSourceUrl('javascript:alert(1)')).toBeNull()
        expect(evidenceSourceUrl('data:text/html,<script>')).toBeNull()
        expect(evidenceSourceUrl(null)).toBeNull()
    })
})

describe('uploadErrorText', () => {

    it('is the message the server gave', () => {
        expect(uploadErrorText(413, {
            status: 413,
            code: 'audit-trail.evidence.too-large',
            message: 'The evidence is bigger than the instance allows.',
        })).toBe('The evidence is bigger than the instance allows.')
    })

    it('falls back on the error of the proxy', () => {
        expect(uploadErrorText(401, {error: 'Unauthorized'})).toBe('Unauthorized')
    })

    it('falls back on the status', () => {
        expect(uploadErrorText(502, null)).toBe('The evidence could not be uploaded (HTTP 502).')
    })
})
