import {isAuthorized} from "@components/common/authorizations";

/**
 * View model of the evidence cell of the validation run page: when it shows, what the user may do,
 * and what may be previewed. Pure functions, so that the cell only draws.
 */

/**
 * Whether the user may upload an evidence to the validation run: `evidence/create`, which the
 * server grants to the creators of validation runs while the licence is on.
 */
export const canUploadEvidence = (run) => !!isAuthorized(run, 'evidence', 'create')

/**
 * Whether the user may delete the evidence of the validation run: `evidence/delete`, which the
 * server grants for `EvidenceDelete` while the licence is on.
 */
export const canDeleteEvidence = (run) => !!isAuthorized(run, 'evidence', 'delete')

/**
 * Whether the validation run page shows its Evidence cell: the run has evidence — deleted ones
 * included, which stay listed — or the user may upload some. A user who can only read sees no
 * empty cell, and the licence never has to be known to the UI: it is folded in `evidence/create`.
 *
 * @param run Validation run, with its `evidence` (their IDs at least) and its `authorizations`
 */
export const showEvidenceCell = (run) =>
    (run?.evidence?.length ?? 0) > 0 || (!!run?.authorizations && canUploadEvidence(run))

/**
 * What the cell says in place of the upload and of the list when the evidence storage cannot be
 * used — `null` when it can, or while its state is not known.
 *
 * @param state `AuditTrailStorageState`
 */
export const evidenceStorageMessage = (state) => {
    switch (state) {
        case 'NOT_CONFIGURED':
            return 'Evidence storage is not configured.'
        case 'UNREACHABLE':
            return 'Evidence storage is unreachable.'
        default:
            return null
    }
}

/**
 * Evidence bigger than this is not previewed: it would be read whole by the browser.
 */
export const PREVIEW_MAX_SIZE = 10 * 1024 * 1024

/**
 * A text preview shows no more than this number of characters.
 */
export const PREVIEW_TEXT_MAX_LENGTH = 256 * 1024

const IMAGE_TYPES = ['image/png', 'image/jpeg', 'image/gif', 'image/webp']

/**
 * How a content of this type is previewed, from the allow-list of the server: PDF, images (PNG,
 * JPEG, GIF, WebP), JSON and plain text — `null` for anything else, HTML and SVG included, and for
 * `application/octet-stream`, which is how the server serves anything it refuses to show inline.
 *
 * Applied to the `Content-Type` of the download, it is the decision to preview: the server only
 * serves an allow-listed type when the content agrees with it. Applied to the declared media type
 * of an evidence, it only decides whether to offer the preview.
 *
 * @param contentType Media type, with its parameters or not
 * @return {'pdf'|'image'|'json'|'text'|null}
 */
export const previewKind = (contentType) => {
    if (!contentType) {
        return null
    }
    const type = contentType.split(';')[0].trim().toLowerCase()
    if (type === 'application/pdf') {
        return 'pdf'
    } else if (IMAGE_TYPES.includes(type)) {
        return 'image'
    } else if (type === 'application/json' || /^application\/[a-z0-9.+-]+\+json$/.test(type)) {
        return 'json'
    } else if (type === 'text/plain') {
        return 'text'
    } else {
        return null
    }
}

/**
 * Whether the cell offers to preview an evidence: it is not deleted, its declared type is one
 * which can be previewed, and it is not too big. The download then has the last word.
 */
export const isPreviewOffered = (evidence) =>
    !evidence.deletedAt &&
    !!evidence.downloadUrl &&
    previewKind(evidence.mediaType) !== null &&
    evidence.size <= PREVIEW_MAX_SIZE

/**
 * The text a preview shows: JSON indented when it parses, kept as it is otherwise, and cut at
 * [PREVIEW_TEXT_MAX_LENGTH]. It is shown as text, never as markup.
 *
 * @param content Content of the evidence
 * @param kind `json` or `text`
 * @return {{text: string, truncated: boolean}}
 */
export const previewText = (content, kind) => {
    let text = content
    if (kind === 'json') {
        try {
            text = JSON.stringify(JSON.parse(content), null, 2)
        } catch {
            text = content
        }
    }
    if (text.length > PREVIEW_TEXT_MAX_LENGTH) {
        return {text: text.substring(0, PREVIEW_TEXT_MAX_LENGTH), truncated: true}
    } else {
        return {text, truncated: false}
    }
}

const SIZE_UNITS = ['KB', 'MB', 'GB', 'TB']

/**
 * Size of an evidence, for a reader.
 *
 * @param size Size in bytes
 */
export const evidenceSize = (size) => {
    if (size < 1024) {
        return `${size} byte${size === 1 ? '' : 's'}`
    }
    let value = size / 1024
    let unit = 0
    while (value >= 1024 && unit < SIZE_UNITS.length - 1) {
        value = value / 1024
        unit++
    }
    return `${value.toFixed(1)} ${SIZE_UNITS[unit]}`
}

/**
 * The URL of the source of an evidence, as a link target: only an HTTP or HTTPS URL — the server
 * refuses anything else, and the UI does not trust it to.
 */
export const evidenceSourceUrl = (url) =>
    url && /^https?:\/\//i.test(url) ? url : null

/**
 * Why an upload failed, from the answer of the server — the `message` of its error, whose `code`
 * is one of `audit-trail.evidence.*` — or of the proxy of the UI.
 *
 * @param status HTTP status of the answer
 * @param body JSON body of the answer, if any
 */
export const uploadErrorText = (status, body) =>
    body?.message || body?.error || `The evidence could not be uploaded (HTTP ${status}).`

/**
 * The evidence the verification found missing or altered, once the evidence was verified.
 *
 * The verification names the failing `evidence.attached` entries by their seq: each is mapped to
 * the evidence its payload records. A seq which is not an `evidence.attached` entry is ignored.
 *
 * @param entries Entries of the trail
 * @param verification `AuditTrailVerification`, whose `missingEvidence` and `alteredEvidence` are
 * `null` until the evidence is verified
 * @return {{verified: boolean, byId: Map<number, 'missing'|'altered'>}} Whether the evidence was
 * verified, and the flag of each failing evidence by its ID
 */
export const evidenceVerificationFlags = (entries, verification) => {
    const missing = verification?.missingEvidence
    const altered = verification?.alteredEvidence
    const byId = new Map()
    const verified = !!missing || !!altered
    if (verified) {
        const evidenceIds = new Map(
            (entries ?? [])
                .filter(entry => entry.type === 'evidence.attached')
                .map(entry => [entry.seq, entry.payload?.evidence?.id])
        )
        const flag = (seqs, value) => (seqs ?? []).forEach(seq => {
            const id = evidenceIds.get(seq)
            if (id !== undefined && id !== null) {
                byId.set(id, value)
            }
        })
        flag(missing, 'missing')
        flag(altered, 'altered')
    }
    return {verified, byId}
}

const distinctSorted = (values) => [...new Set(values.filter(value => !!value))].sort()

/**
 * What the filters of the evidence of a build offer to pick: the media types and the validation
 * stamps present among the evidence, sorted, once each.
 *
 * @param evidence Evidence of the build, each with its `validationRun`
 */
export const evidenceFilterOptions = (evidence) => ({
    mediaTypes: distinctSorted(evidence.map(item => item.mediaType)),
    validationStamps: distinctSorted(evidence.map(item => item.validationRun?.validationStamp?.name)),
})

/**
 * State of an evidence for the State filter: `active` or `deleted`, and `failing` too when the
 * verification flagged it.
 */
const evidenceStates = (item, flags) => {
    const states = [item.deletedAt ? 'deleted' : 'active']
    if (flags?.byId?.has(item.id)) {
        states.push('failing')
    }
    return states
}

const matchesText = (item, text) => {
    const needle = text.trim().toLowerCase()
    return !needle ||
        item.fileName.toLowerCase().includes(needle) ||
        item.sha256.toLowerCase().startsWith(needle)
}

const matchesAny = (picked, values) => !picked || picked.length === 0 || values.some(value => picked.includes(value))

/**
 * The evidence of a build kept by the filters of its table. An absent or empty filter keeps
 * everything; the filters combine.
 *
 * @param evidence Evidence of the build, each with its `validationRun`
 * @param filters `text` — matching the name, ignoring the case, or the start of the SHA-256;
 * `mediaTypes`, `validationStamps` — those picked; `states` — among `active`, `deleted` and
 * `failing`
 * @param flags Flags of the verification, from {@link evidenceVerificationFlags}
 */
export const filterEvidence = (evidence, {text, mediaTypes, validationStamps, states} = {}, flags) =>
    evidence.filter(item =>
        matchesText(item, text ?? '') &&
        matchesAny(mediaTypes, [item.mediaType]) &&
        matchesAny(validationStamps, [item.validationRun?.validationStamp?.name]) &&
        matchesAny(states, evidenceStates(item, flags))
    )
