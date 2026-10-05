import {
    canDeleteEvidence,
    canUploadEvidence,
    evidenceSize,
    evidenceSourceUrl,
    evidenceFilterOptions,
    evidenceStorageMessage,
    evidenceVerificationFlags,
    filterEvidence,
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

const buildEvidence = (id, overrides = {}) => evidence({
    id,
    sha256: `${id}`.repeat(64).substring(0, 64),
    validationRun: {id: 100 + id, runOrder: 1, validationStamp: {name: 'scan'}},
    ...overrides,
})

const attached = (seq, evidenceId) => ({seq, type: 'evidence.attached', payload: {evidence: {id: evidenceId}}})

describe('evidenceVerificationFlags', () => {

    const entries = [
        {seq: 1, type: 'build.created', payload: {}},
        attached(2, 10),
        attached(3, 11),
        attached(4, 12),
        {seq: 5, type: 'evidence.deleted', payload: {evidence: {id: 12}}},
    ]

    it('flags nothing while the evidence was not verified', () => {
        const flags = evidenceVerificationFlags(entries, {missingEvidence: null, alteredEvidence: null})
        expect(flags.verified).toBe(false)
        expect(flags.byId.size).toBe(0)
    })

    it('flags nothing when the evidence is intact', () => {
        const flags = evidenceVerificationFlags(entries, {missingEvidence: [], alteredEvidence: []})
        expect(flags.verified).toBe(true)
        expect(flags.byId.size).toBe(0)
    })

    it('maps the seqs of the failing entries to their evidence', () => {
        const flags = evidenceVerificationFlags(entries, {missingEvidence: [2], alteredEvidence: [4]})
        expect(flags.verified).toBe(true)
        expect(flags.byId.get(10)).toBe('missing')
        expect(flags.byId.get(11)).toBeUndefined()
        expect(flags.byId.get(12)).toBe('altered')
    })

    it('ignores a seq which is not an evidence.attached entry', () => {
        const flags = evidenceVerificationFlags(entries, {missingEvidence: [1, 5], alteredEvidence: []})
        expect(flags.byId.size).toBe(0)
    })
})

describe('evidenceFilterOptions', () => {

    it('lists the media types and the validation stamps present, sorted, once each', () => {
        const options = evidenceFilterOptions([
            buildEvidence(1, {mediaType: 'image/png', validationRun: {validationStamp: {name: 'tests'}}}),
            buildEvidence(2, {mediaType: 'application/pdf'}),
            buildEvidence(3, {mediaType: 'image/png'}),
        ])
        expect(options.mediaTypes).toEqual(['application/pdf', 'image/png'])
        expect(options.validationStamps).toEqual(['scan', 'tests'])
    })

    it('lists nothing for no evidence', () => {
        expect(evidenceFilterOptions([])).toEqual({mediaTypes: [], validationStamps: []})
    })
})

describe('filterEvidence', () => {

    const items = [
        buildEvidence(1, {fileName: 'Trivy-Report.pdf', sha256: 'abcdef' + '0'.repeat(58)}),
        buildEvidence(2, {fileName: 'sbom.json', mediaType: 'application/json', validationRun: {validationStamp: {name: 'sbom'}}}),
        buildEvidence(3, {fileName: 'old.pdf', deletedAt: '2026-10-01T10:00:00Z', downloadUrl: null}),
        buildEvidence(4, {fileName: 'screenshot.png', mediaType: 'image/png', sha256: 'fedcba' + '1'.repeat(58)}),
    ]
    const noFlags = {verified: false, byId: new Map()}
    const ids = (filtered) => filtered.map(item => item.id)

    it('keeps everything without a filter', () => {
        expect(ids(filterEvidence(items, {}, noFlags))).toEqual([1, 2, 3, 4])
    })

    it('matches the text against the name, ignoring the case', () => {
        expect(ids(filterEvidence(items, {text: 'report'}, noFlags))).toEqual([1])
    })

    it('matches the text against the start of the SHA-256, ignoring the case', () => {
        expect(ids(filterEvidence(items, {text: 'FEDC'}, noFlags))).toEqual([4])
        // Not in the middle of the SHA-256
        expect(ids(filterEvidence(items, {text: 'cdef'}, noFlags))).toEqual([])
    })

    it('ignores a blank text', () => {
        expect(ids(filterEvidence(items, {text: '  '}, noFlags))).toEqual([1, 2, 3, 4])
    })

    it('keeps the media types picked', () => {
        expect(ids(filterEvidence(items, {mediaTypes: ['application/json', 'image/png']}, noFlags))).toEqual([2, 4])
    })

    it('keeps the validation stamps picked', () => {
        expect(ids(filterEvidence(items, {validationStamps: ['sbom']}, noFlags))).toEqual([2])
    })

    it('keeps the active or the deleted evidence', () => {
        expect(ids(filterEvidence(items, {states: ['active']}, noFlags))).toEqual([1, 2, 4])
        expect(ids(filterEvidence(items, {states: ['deleted']}, noFlags))).toEqual([3])
        expect(ids(filterEvidence(items, {states: ['active', 'deleted']}, noFlags))).toEqual([1, 2, 3, 4])
    })

    it('keeps the evidence failing the verification', () => {
        const flags = {verified: true, byId: new Map([[2, 'missing'], [4, 'altered']])}
        expect(ids(filterEvidence(items, {states: ['failing']}, flags))).toEqual([2, 4])
        expect(ids(filterEvidence(items, {states: ['deleted', 'failing']}, flags))).toEqual([2, 3, 4])
    })

    it('combines the filters', () => {
        expect(ids(filterEvidence(items, {text: '.p', mediaTypes: ['application/pdf'], states: ['active']}, noFlags))).toEqual([1])
    })
})
