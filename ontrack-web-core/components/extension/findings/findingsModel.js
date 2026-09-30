/**
 * The vocabulary of the findings, as the UI shows it, and the filter of the findings of a project
 * as the URL of the project findings page carries it.
 */

/** Severities, the most severe first, as the server orders them. */
export const FINDING_SEVERITIES = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'UNKNOWN']

/** States of a finding, in its project or on a branch. */
export const FINDING_STATES = ['OPEN', 'ACCEPTED', 'RESOLVED']

/** Kinds of scan. */
export const FINDING_KINDS = ['IMAGE', 'CODE', 'SECRETS', 'DAST', 'DEPENDENCIES', 'OTHER']

/** The criteria of the filter, in the order of the URL. */
export const FINDINGS_FILTER_FIELDS = ['severity', 'state', 'branch', 'scanner', 'kind']

const ENUMS = {
    severity: FINDING_SEVERITIES,
    state: FINDING_STATES,
    kind: FINDING_KINDS,
}

const single = (value) => Array.isArray(value) ? value[0] : value

/**
 * The filter of the findings from the query of the URL.
 *
 * A criterion which is absent, blank, or not one of the values of its enumeration is left out,
 * so that a hand-edited URL can never send the server a filter it would refuse.
 *
 * @param {object} query The query of the router
 * @returns {object} The filter, with only the criteria which apply
 */
export function findingsFilterFromQuery(query = {}) {
    const filter = {}
    FINDINGS_FILTER_FIELDS.forEach(field => {
        const value = single(query[field])
        if (typeof value === 'string' && value.trim() !== '') {
            const allowed = ENUMS[field]
            if (!allowed || allowed.includes(value)) {
                filter[field] = value
            }
        }
    })
    return filter
}

/**
 * The query of the URL from a filter of the findings: the criteria which apply only.
 */
export function findingsFilterToQuery(filter = {}) {
    const query = {}
    FINDINGS_FILTER_FIELDS.forEach(field => {
        const value = filter[field]
        if (value !== undefined && value !== null && value !== '') {
            query[field] = value
        }
    })
    return query
}

/** Human names of the severities. */
export const severityName = (severity) =>
    severity ? severity.charAt(0) + severity.slice(1).toLowerCase() : ''

/**
 * The one palette of the severities.
 *
 * - `preset`: the antd colour of the light severity tag (`FindingSeverityTag`)
 * - `background` and `text`: the colours of a solid tag or badge (`FindingSeverityCountTag`), the
 *   darker shade of the same hue, and a text colour keeping the pair at WCAG AA contrast (4.5:1)
 *   whatever the theme — dark text on gold, white on the others.
 */
const FINDING_SEVERITY_PALETTE = {
    CRITICAL: {preset: 'red', background: '#cf1322', text: '#ffffff'},
    HIGH: {preset: 'volcano', background: '#d4380d', text: '#ffffff'},
    MEDIUM: {preset: 'gold', background: '#faad14', text: '#1f1f1f'},
    LOW: {preset: 'blue', background: '#0958d9', text: '#ffffff'},
    UNKNOWN: {preset: 'default', background: '#595959', text: '#ffffff'},
}

/**
 * Colours of a severity: `{preset, background, text}`, the ones of the unknown severity for
 * anything else.
 */
export const findingSeverityColor = (severity) =>
    FINDING_SEVERITY_PALETTE[severity] ?? FINDING_SEVERITY_PALETTE.UNKNOWN

/** Number of findings of a severity, in a list of `{severity, count}`. */
export const severityCount = (counts, severity) =>
    (counts ?? []).find(it => it.severity === severity)?.count ?? 0

/**
 * The most severe severity having an open finding, `null` when none is open.
 *
 * @param {Array} open `{severity, count}` per severity
 */
export const highestOpenSeverity = (open) =>
    FINDING_SEVERITIES.find(severity => severityCount(open, severity) > 0) ?? null

/**
 * Whether a summary of findings — of a project or of a branch — has any finding at all, open,
 * accepted or resolved.
 */
export const hasReportedFindings = (summary) =>
    !!summary && (summary.openCount + summary.acceptedCount + summary.resolvedCount) > 0

/** "1 finding", "2 findings". */
export const findingsCountText = (count, qualifier = '') =>
    `${count} ${qualifier ? `${qualifier} ` : ''}${count === 1 ? 'finding' : 'findings'}`

/**
 * The open findings spelled out, for a label: `3 open findings: 1 critical, 2 high`.
 *
 * @param {Array} open `{severity, count}` per severity
 */
export function openFindingsLabel(open) {
    const total = (open ?? []).reduce((sum, it) => sum + it.count, 0)
    if (total === 0) return 'No open finding'
    const details = FINDING_SEVERITIES
        .filter(severity => severityCount(open, severity) > 0)
        .map(severity => `${severityCount(open, severity)} ${severity.toLowerCase()}`)
        .join(', ')
    return `${findingsCountText(total, 'open')}: ${details}`
}

/** Human names of the states. */
export const stateName = (state) =>
    state ? state.charAt(0) + state.slice(1).toLowerCase() : ''

/** Human names of the kinds. */
export const kindName = (kind) => {
    switch (kind) {
        case 'DAST':
            return 'DAST'
        default:
            return kind ? kind.charAt(0) + kind.slice(1).toLowerCase() : ''
    }
}

/**
 * The branches a finding is exposed on today, from its exposure per branch and stamp: a branch
 * is listed once, exposed as soon as one of its stamps reports the finding without any
 * acceptance holding, else accepted. Branches where the finding is resolved are left out.
 *
 * @param {Array} exposures The `exposures` of a finding: `{branch, state}` per branch and stamp
 * @returns {Array} `{branch, state}` per branch, in the order of the exposures
 */
export function exposedBranches(exposures = []) {
    const byBranch = new Map()
    exposures.forEach(({branch, state}) => {
        if (state === 'EXPOSED' || state === 'ACCEPTED') {
            const current = byBranch.get(branch.id)
            if (!current) {
                byBranch.set(branch.id, {branch, state})
            } else if (state === 'EXPOSED') {
                current.state = 'EXPOSED'
            }
        }
    })
    return [...byBranch.values()]
}

/**
 * The exposure of a finding as the rows of a table, one per branch and stamp, in the order of the
 * exposures — by branch name then stamp name, as the server gives them. The first row of a branch
 * spans its other ones (`branchRowSpan`), which span none.
 *
 * @param {Array} exposures The `exposures` of a finding, with their `branch` and `validationStamp`
 * @returns {Array} The exposures, each with a `key` and a `branchRowSpan`
 */
export function exposureRows(exposures = []) {
    const counts = new Map()
    exposures.forEach(({branch}) => counts.set(branch.id, (counts.get(branch.id) ?? 0) + 1))
    const seen = new Set()
    return exposures.map(exposure => {
        const branchId = exposure.branch.id
        const first = !seen.has(branchId)
        seen.add(branchId)
        return {
            ...exposure,
            key: `${branchId}-${exposure.validationStamp.id}`,
            branchRowSpan: first ? counts.get(branchId) : 0,
        }
    })
}

/**
 * One line about an acceptance: whether there is one, whether it still holds, and until when.
 *
 * @param {object} acceptance `{effective, expiresAt}`, or nothing
 */
export function acceptanceSummary(acceptance) {
    if (!acceptance) {
        return 'Not accepted'
    } else if (!acceptance.effective) {
        return `Acceptance expired on ${acceptance.expiresAt}`
    } else if (acceptance.expiresAt) {
        return `Accepted until ${acceptance.expiresAt}`
    } else {
        return 'Accepted, without expiry'
    }
}

/** Validation data type of the reports of security scans. */
export const FINDINGS_VALIDATION_DATA_TYPE = 'net.nemerosa.ontrack.extension.findings.validation.FindingsValidationDataType'

/**
 * Whether a validation run is a security scan: its data is a report of security scan, or, for a
 * run without data, its stamp takes such reports.
 */
export function isFindingsRun(run) {
    return run?.data?.descriptor?.id === FINDINGS_VALIDATION_DATA_TYPE ||
        run?.validationStamp?.dataType?.descriptor?.id === FINDINGS_VALIDATION_DATA_TYPE
}
