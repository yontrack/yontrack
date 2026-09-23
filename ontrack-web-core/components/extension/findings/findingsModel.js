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
