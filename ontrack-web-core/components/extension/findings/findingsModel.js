/**
 * The vocabulary of the findings, as the UI shows it, and the filter of the findings of a project
 * as the URL of the project findings page carries it.
 */

import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import {UNKNOWN_BUILD} from "@components/extension/findings/finding/PeriodBuild";

dayjs.extend(utc);

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

const HOUR = 3600
const DAY = 24 * HOUR

/**
 * How long an exposure lasted: minutes under an hour, hours under 48 hours, whole days above.
 *
 * @param {number} seconds Duration, in seconds
 * @param {boolean} ongoing Whether the exposure is still open
 * @returns {string} "35 min", "31 h", "4 days" — with ", ongoing" for an open exposure
 */
export function formatExposureDuration(seconds, ongoing = false) {
    if (seconds === null || seconds === undefined) return ''
    let text
    if (seconds < HOUR) {
        text = `${Math.max(1, Math.floor(seconds / 60))} min`
    } else if (seconds < 2 * DAY) {
        text = `${Math.floor(seconds / HOUR)} h`
    } else {
        text = `${Math.floor(seconds / DAY)} days`
    }
    return ongoing ? `${text}, ongoing` : text
}

/**
 * The periods of an exposure before its current one, in one line: "earlier: 19 h, 2.5.0 → 2.5.1".
 *
 * @param {Array} periods The `periods` of an exposure, the oldest first
 * @returns {string} The summary, empty for an exposure with one period
 */
export function earlierPeriodsSummary(periods = []) {
    const earlier = (periods ?? []).slice(0, -1)
    if (earlier.length === 0) return ''
    const build = (name) => name ?? UNKNOWN_BUILD
    return 'earlier: ' + earlier
        .map(p => `${formatExposureDuration(p.durationSeconds)}, ${build(p.startedInBuild)} → ${build(p.endedInBuild)}`)
        .join('; ')
}

/**
 * The exposure of a finding laid out on a time axis, from the start of its earliest period to
 * now: one lane per branch and stamp, in the order of the exposure table, each with a bar per
 * period and, over a bar, its stretches under an acceptance. Positions and widths are percentages
 * of the axis.
 *
 * While a filter is active, a period which ended reads against its `boundary`:
 *
 * - `fixDot` is the dot at the end of the red bar: `HOLLOW` for a scan fix, `SOLID` when this build is
 *   the matching one (and always without a filter), `null` while the period is open;
 * - `wait` is the hatched stretch, from the scan fix to the matching build, to now, or to the reopening;
 * - `matchDot` is where the matching build is reached, when it is not the fixing one.
 *
 * @param {Array} exposures The `exposures` of a finding, with their `periods`
 * @param {Date} now End of the axis
 * @param {boolean} filtered Whether a filter is active
 * @returns {object|null} `{start, end, lanes, ticks}`, `null` when there is no period
 */
export function exposureTimeline(exposures = [], now = new Date(), {filtered = false} = {}) {
    const time = (value) => dayjs.utc(value).valueOf()
    const periods = exposures.flatMap(exposure => exposure.periods ?? [])
    if (periods.length === 0) return null

    const start = Math.min(...periods.map(p => time(p.startedAt)))
    const boundaryTimes = filtered ? periods.filter(p => p.boundary?.time).map(p => time(p.boundary.time)) : []
    const end = Math.max(now.getTime(), ...periods.filter(p => p.endedAt).map(p => time(p.endedAt)), ...boundaryTimes)
    const span = Math.max(end - start, 1)
    const at = (t) => ((t - start) / span) * 100

    const stamps = new Map()
    exposures.forEach(({branch}) => stamps.set(branch.id, (stamps.get(branch.id) ?? 0) + 1))

    const lanes = exposureRows(exposures).map(row => ({
        key: row.key,
        branch: row.branch,
        validationStamp: row.validationStamp,
        showStamp: stamps.get(row.branch.id) > 1,
        bars: (row.periods ?? []).map((period, index) => {
            const from = time(period.startedAt)
            const to = period.endedAt ? time(period.endedAt) : end
            const nextPeriod = row.periods[index + 1] ?? null
            const boundary = filtered && period.endedAt ? period.boundary ?? null : null
            const onFixingBuild = isBoundaryOnFixingBuild(period, boundary)
            const waitUntil = boundaryWaitUntil(boundary, onFixingBuild, nextPeriod, end, time)
            let fixDot = null
            if (period.endedAt) {
                fixDot = !boundary || onFixingBuild ? 'SOLID' : 'HOLLOW'
            }
            return {
                period,
                nextPeriod,
                boundary,
                onFixingBuild,
                left: at(from),
                width: at(to) - at(from),
                fixed: !!period.endedAt,
                fixDot,
                wait: waitUntil !== null ? {left: at(to), width: Math.max(at(waitUntil) - at(to), 0)} : null,
                matchDot: boundary?.status === 'MATCHED' && !onFixingBuild ? at(waitUntil) : null,
                accepted: (period.acceptedSpans ?? []).map(acceptedSpan => {
                    const spanFrom = time(acceptedSpan.from)
                    const spanTo = acceptedSpan.to ? time(acceptedSpan.to) : to
                    return {span: acceptedSpan, left: at(spanFrom), width: at(spanTo) - at(spanFrom)}
                }),
            }
        }),
    }))

    const TICKS = 6
    const ticks = Array.from({length: TICKS + 1}, (_, index) => {
        const t = start + (span * index) / TICKS
        return {key: index, left: at(t), time: new Date(t)}
    })

    return {start: new Date(start), end: new Date(end), lanes, ticks}
}

/**
 * Whether the matching build of a boundary is the build which ended the period. Once this one has been
 * purged, it cannot be.
 */
function isBoundaryOnFixingBuild(period, boundary) {
    const fixingBuild = period.endedBy?.build?.id
    return boundary?.status === 'MATCHED' && fixingBuild !== undefined && String(boundary.build?.id) === String(fixingBuild)
}

/**
 * Where the hatched stretch of a period stops, `null` when it has none.
 */
function boundaryWaitUntil(boundary, onFixingBuild, nextPeriod, end, time) {
    switch (boundary?.status) {
        case 'MATCHED':
            return onFixingBuild ? null : time(boundary.time)
        case 'NONE_YET':
            return end
        case 'NOT_REACHED':
            return nextPeriod ? time(nextPeriod.startedAt) : null
        default:
            return null
    }
}

/** Type of the build filters which can set a boundary. */
export const STANDARD_BUILD_FILTER_TYPE = 'net.nemerosa.ontrack.service.StandardBuildFilterProvider'

/** The short name of a boundary filter which is not about one promotion level. */
const MATCHING_BUILD = 'a matching build'

/** The criteria whose result moves with the head of the branch: they cannot set a boundary. */
const SINCE_CRITERIA = [
    'sincePromotionLevel',
    'sinceValidationStamp',
    'sinceValidationStampStatus',
    'sinceProperty',
    'sincePropertyValue',
]

const BOUNDARY_DATE_CRITERIA = ['afterDate', 'beforeDate']

/** The criteria testing a single build, the only ones which can set a boundary. */
const BOUNDARY_FILTER_CRITERIA = [
    'withPromotionLevel',
    'withValidationStamp',
    'withValidationStampStatus',
    'withProperty',
    'withPropertyValue',
    'linkedFrom',
    'linkedFromPromotion',
    'linkedTo',
    'linkedToPromotion',
    'withDisplayName',
    ...BOUNDARY_DATE_CRITERIA,
    'assisted',
    'actor',
]

const usesSinceCriterion = (data) => SINCE_CRITERIA.some(field => !!data?.[field])

/**
 * The usable criteria of the data of a build filter, always in the same order: no `count`, no `since*`,
 * no name, nothing empty, and the dates as days.
 */
export function boundaryFilterCriteria(data) {
    const criteria = {}
    BOUNDARY_FILTER_CRITERIA.forEach(field => {
        const value = data?.[field]
        if (value !== undefined && value !== null && value !== '') {
            criteria[field] = BOUNDARY_DATE_CRITERIA.includes(field) ? dayjs(value).format('YYYY-MM-DD') : value
        }
    })
    return criteria
}

/**
 * A boundary filter, `{name, data}`, from a value coming from outside (a permalink, the local storage).
 *
 * @returns {object|undefined} The filter, `undefined` when it has no usable criterion, or uses a
 * `since*` one, which cannot set a boundary
 */
export function sanitizeBoundaryFilter(value) {
    if (!value || typeof value !== 'object' || usesSinceCriterion(value.data)) return undefined
    const data = boundaryFilterCriteria(value.data)
    if (Object.keys(data).length === 0) return undefined
    return typeof value.name === 'string' && value.name ? {name: value.name, data} : {data}
}

/** A boundary filter from the JSON of a permalink, `undefined` when it is not one. */
export function parseBoundaryFilter(json) {
    if (!json) return undefined
    try {
        return sanitizeBoundaryFilter(JSON.parse(json))
    } catch (ignored) {
        return undefined
    }
}

/**
 * The short name of a boundary filter in the texts: the promotion level when the filter has only this
 * criterion, else "a matching build".
 */
export function boundaryFilterShortName(filter) {
    const criteria = Object.keys(filter?.data ?? {})
    return criteria.length === 1 && criteria[0] === 'withPromotionLevel' ? filter.data.withPromotionLevel : MATCHING_BUILD
}

/** What the picker shows for a boundary filter. */
export function boundaryFilterLabel(filter) {
    if (!filter) return 'No filter (all builds)'
    if (filter.name) return filter.name
    const shortName = boundaryFilterShortName(filter)
    return shortName === MATCHING_BUILD ? 'Custom filter' : `With promotion ${shortName}`
}

/** Why a saved filter cannot set a boundary, `undefined` when it can. */
function savedFilterDisabledReason(data, criteria) {
    if (usesSinceCriterion(data)) return `uses "since", can't set a boundary`
    if (Object.keys(criteria).length === 0) return `tests no build, can't set a boundary`
    return undefined
}

/**
 * The saved filters a boundary filter can be copied from: the standard ones of the given branches. A
 * filter with the same name and the same criteria on several branches is listed once.
 *
 * @param {Array} branches `{name, buildFilterResources}` per branch
 * @returns {Array} `{key, name, branches, filter}`, or `{key, name, branches, disabledReason}` for a
 * filter which cannot be selected
 */
export function savedBoundaryFilters(branches = []) {
    const saved = new Map()
    branches.forEach(branch => {
        (branch.buildFilterResources ?? [])
            .filter(resource => resource.type === STANDARD_BUILD_FILTER_TYPE && !resource.error)
            .forEach(resource => {
                const data = boundaryFilterCriteria(resource.data)
                const disabledReason = savedFilterDisabledReason(resource.data, data)
                const key = JSON.stringify({name: resource.name, disabledReason, data})
                if (saved.has(key)) {
                    saved.get(key).branches.push(branch.name)
                } else if (disabledReason) {
                    saved.set(key, {key, name: resource.name, branches: [branch.name], disabledReason})
                } else {
                    saved.set(key, {key, name: resource.name, branches: [branch.name], filter: {name: resource.name, data}})
                }
            })
    })
    return [...saved.values()]
}

const inABuild = (shortName) => {
    if (shortName === MATCHING_BUILD) return MATCHING_BUILD
    return `${/^[aeiou]/i.test(shortName) ? 'an' : 'a'} ${shortName} build`
}

const noBuildYet = (shortName) =>
    shortName === MATCHING_BUILD ? 'no matching build yet' : `no ${shortName} build yet`

const capitalized = (text) => text.charAt(0).toUpperCase() + text.slice(1)

const boundaryDay = (boundary) => dayjs.utc(boundary.time).local().format('D MMM')

/** The name of the matching build of a boundary. */
export const boundaryBuildName = (boundary) => boundary?.build?.displayName ?? boundary?.build?.name ?? UNKNOWN_BUILD

/** How the step to the matching build reads: "→ GOLD in 3.4.4", "→ matching in 3.4.4". */
export const boundaryStepName = (shortName) => shortName === MATCHING_BUILD ? 'matching' : shortName

/** The filtered duration of a period: "12 days until GOLD", empty when it has none. */
export function boundaryDurationText(boundary, shortName) {
    switch (boundary?.status) {
        case 'MATCHED':
            return `${formatExposureDuration(boundary.durationSeconds)} until ${shortName}`
        case 'NONE_YET':
            return `${formatExposureDuration(boundary.durationSeconds)} so far until ${shortName}`
        default:
            return ''
    }
}

/** When a build started to match: "GOLD on 14 Oct". */
export const boundaryMatchedText = (boundary, shortName) =>
    `${boundaryStepName(shortName)} on ${boundaryDay(boundary)}`

/**
 * How the label of a bar ends, after its builds: "4 days, 12 until GOLD". The unit is said once when
 * both durations are in days.
 */
export function boundaryBarText(period, boundary, shortName) {
    const raw = formatExposureDuration(period.durationSeconds)
    const filtered = formatExposureDuration(boundary.durationSeconds)
    const days = ' days'
    const short = raw.endsWith(days) && filtered.endsWith(days) ? filtered.slice(0, -days.length) : filtered
    switch (boundary.status) {
        case 'MATCHED':
            return `${raw}, ${short} until ${shortName}`
        case 'NONE_YET':
            return `${noBuildYet(shortName)} · ${raw}, ${short} so far`
        case 'NOT_REACHED':
            return `${raw} · not reached`
        default:
            return `${raw} · doesn't apply`
    }
}

/**
 * A boundary in one sentence, for the label and the tooltip of a bar: colour is never the only signal.
 *
 * @param {object} boundary The boundary of the period
 * @param {string} shortName Short name of the filter
 * @param {object} nextPeriod Next period of the exposure, if any
 */
export function boundaryLabel(boundary, shortName, nextPeriod) {
    const duration = formatExposureDuration(boundary?.durationSeconds)
    switch (boundary?.status) {
        case 'MATCHED':
            return `In ${inABuild(shortName)}, ${boundaryBuildName(boundary)}, on ${boundaryDay(boundary)}, ${duration}.`
        case 'NONE_YET':
            return `${capitalized(noBuildYet(shortName))}, ${duration} so far.`
        case 'NOT_REACHED':
            return `Reopened in ${nextPeriod?.startedInBuild ?? UNKNOWN_BUILD} before reaching ${inABuild(shortName)}.`
        case 'NOT_APPLICABLE':
            return `The filter does not apply: ${boundary.reason}`
        default:
            return ''
    }
}

/** The last period of an exposure which ended, `null` when none did. */
export function lastEndedPeriod(periods) {
    return [...(periods ?? [])].reverse().find(period => period.endedAt) ?? null
}

/**
 * The exposures of a finding with the boundaries of their periods, read apart from them so that the
 * page does not reload when the filter changes.
 *
 * @param {Array} exposures The `exposures` of a finding
 * @param {Array} boundaries The same exposures, whose periods have a `startedAt` and a `boundary`
 */
export function withBoundaries(exposures = [], boundaries = []) {
    const key = (exposure) => `${exposure.branch.id}-${exposure.validationStamp.id}`
    const byExposure = new Map(boundaries.map(exposure => [key(exposure), exposure.periods ?? []]))
    return exposures.map(exposure => {
        const periods = byExposure.get(key(exposure)) ?? []
        return {
            ...exposure,
            periods: exposure.periods?.map(period => {
                const read = periods.find(it => it.startedAt === period.startedAt)
                return read ? {...period, boundary: read.boundary} : period
            }),
        }
    })
}

/** The default order of the findings of a project: the most severe first, then the most recently seen. */
export const DEFAULT_FINDINGS_SORT = 'DEFAULT'

/** The findings of a project the longest exposed first. */
export const EXPOSED_FOR_FINDINGS_SORT = 'EXPOSED_FOR'

/** Orders of the findings of a project, as `Project.findings(sort)` takes them. */
export const FINDINGS_SORTS = [DEFAULT_FINDINGS_SORT, EXPOSED_FOR_FINDINGS_SORT]

/**
 * The order of the findings from the query of the URL: `DEFAULT` unless it says another one of
 * {@link FINDINGS_SORTS}.
 */
export function findingsSortFromQuery(query = {}) {
    const value = single(query.sort)
    return FINDINGS_SORTS.includes(value) ? value : DEFAULT_FINDINGS_SORT
}

/** The query of the URL from an order of the findings: nothing for the default one. */
export function findingsSortToQuery(sort) {
    return sort && sort !== DEFAULT_FINDINGS_SORT ? {sort} : {}
}

const YEAR = 365 * DAY

/**
 * Length of the age bar of an exposure, as a ratio of its full length: on a log scale from an hour
 * (empty) to a year (full), whatever the page or the filter, so that bars can be compared.
 *
 * @param {number} seconds Duration of the exposure, in seconds
 * @returns {number} Between 0 and 1
 */
export function exposureBarRatio(seconds) {
    if (!seconds || seconds <= HOUR) return 0
    if (seconds >= YEAR) return 1
    return Math.log(seconds / HOUR) / Math.log(YEAR / HOUR)
}

/**
 * How long a finding has been exposed, in one line: the duration of its longest ongoing period, or
 * "fixed after X" for a fixed finding, X being the length of its last exposure episode.
 *
 * @param {object} exposedFor The `exposedFor` of a finding
 * @returns {string} The text, empty for a finding without any period
 */
export function exposedForText(exposedFor) {
    if (!exposedFor) return ''
    if (exposedFor.ongoing) return formatExposureDuration(exposedFor.ongoingSeconds)
    if (exposedFor.lastEpisodeSeconds !== null && exposedFor.lastEpisodeSeconds !== undefined) {
        return `fixed after ${formatExposureDuration(exposedFor.lastEpisodeSeconds)}`
    }
    return ''
}
