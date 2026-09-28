/**
 * What the scorecard UI makes of the readings of `Project.scorecard`: names, units, the judgement
 * against a target, and the wording of the `details`. Pure functions, shared by the Scorecard section
 * of the project page and the scorecard page of the project.
 *
 * Units, as the API gives them: durations in seconds, frequencies per week, rates in percent (0 to 100).
 */

export const DURATION = 'duration'
export const PER_WEEK = 'perWeek'
export const PERCENT = 'percent'

const LOWER_IS_BETTER = 'LOWER_IS_BETTER'
const HIGHER_IS_BETTER = 'HIGHER_IS_BETTER'

/**
 * The readings of the catalogue, in its order, with their name, unit, and the way they are better
 * (the direction a target judges them in, fixed by the reading).
 */
export const READINGS = [
    {key: 'delivery.leadTime', name: 'Lead time', unit: DURATION, marker: true, direction: LOWER_IS_BETTER},
    {key: 'delivery.frequency', name: 'Frequency', unit: PER_WEEK, marker: true, direction: HIGHER_IS_BETTER},
    {key: 'delivery.successRate', name: 'Success rate', unit: PERCENT, marker: true, direction: HIGHER_IS_BETTER},
    {key: 'delivery.mttr', name: 'Time to restore', unit: DURATION, marker: true, direction: LOWER_IS_BETTER},
    {key: 'quality.testPassRate', name: 'Test pass rate', unit: PERCENT, marker: false, direction: HIGHER_IS_BETTER},
    {key: 'quality.testFlakiness', name: 'Test flakiness', unit: PERCENT, marker: false, direction: LOWER_IS_BETTER},
]

const readingOf = (key) => READINGS.find(it => it.key === key)

/**
 * Unit of a reading — `DURATION`, `PER_WEEK` or `PERCENT` — `null` out of the catalogue.
 */
export const readingUnit = (key) => readingOf(key)?.unit ?? null

/**
 * Position of a reading in the catalogue, the readings out of it last.
 */
export const readingRank = (key) => {
    const index = READINGS.findIndex(it => it.key === key)
    return index >= 0 ? index : READINGS.length
}

/**
 * Name of a reading, its key for a reading out of the catalogue.
 */
export const readingName = (key) => readingOf(key)?.name ?? key

/**
 * Whether a reading is read up to the marker. The test readings read the branches in scope and
 * never the marker, even though their details carry it.
 */
export const readingUsesMarker = (key) => readingOf(key)?.marker ?? true

const isNumber = (value) => typeof value === 'number' && !isNaN(value)

/**
 * Rounds a number to at most `digits` decimals, without trailing zeros.
 */
const round = (value, digits) => String(Number(value.toFixed(digits)))

const DURATION_UNITS = [
    ['d', 86400],
    ['h', 3600],
    ['m', 60],
    ['s', 1],
]

/**
 * A duration in seconds, in its two largest units: `2m 30s`, `1h 2m`, `1d 1h`, `10d`; `< 1s` for a
 * duration shorter than half a second, which is not nothing.
 */
export const formatDuration = (seconds) => {
    if (!isNumber(seconds) || seconds < 0) return '-'
    if (seconds === 0) return '0s'
    let rest = Math.round(seconds)
    // A measured duration never reads 0
    if (rest === 0) return '< 1s'
    const parts = []
    for (const [unit, size] of DURATION_UNITS) {
        const count = Math.floor(rest / size)
        rest = rest % size
        if (parts.length > 0 || count > 0) {
            parts.push(count > 0 ? `${count}${unit}` : null)
        }
        if (parts.length === 2) break
    }
    return parts.filter(it => it).join(' ')
}

/**
 * A value in the unit of its reading.
 */
export const formatReadingValue = (key, value) => {
    if (!isNumber(value)) return '-'
    switch (readingOf(key)?.unit) {
        case DURATION:
            return formatDuration(value)
        case PER_WEEK:
            return `${value >= 1 ? round(value, 1) : round(value, 2)} / week`
        case PERCENT:
            return `${round(value, 1)}%`
        default:
            return round(value, 2)
    }
}

const UNKNOWN_REASONS = {
    NO_MARKER: 'No marker: no promotion level on the branches read, or no slot of the project in the marker environment',
    NO_SAMPLES: 'Nothing reached the marker in the window',
    NO_FAILURE: 'No failure in window',
    NO_TEST_STAMP: 'No test stamp: no validation stamp with test summary data on the branches read',
    NOT_LICENSED: 'The environment marker needs the environments, which the licence does not allow',
}

/**
 * Why a reading is unknown, in words.
 */
export const unknownReasonText = (reason) => UNKNOWN_REASONS[reason] ?? reason

/**
 * How a reading is to be shown:
 *
 * - `MET` / `MISSED` — measured, against the target of its estate
 * - `SHOWN` — measured, with no target: shown, not judged
 * - `NO_FAILURE` — a time to restore with nothing to restore, rendered neutral rather than unknown
 * - `UNKNOWN` — Yontrack cannot tell, for the `unknownReason`
 */
export const readingJudgement = (reading) => {
    if (reading.unknownReason === 'NO_FAILURE') return 'NO_FAILURE'
    if (reading.basis === 'UNKNOWN' || !isNumber(reading.value)) return 'UNKNOWN'
    if (reading.targetMet === true) return 'MET'
    if (reading.targetMet === false) return 'MISSED'
    return 'SHOWN'
}

const DIRECTION_SYMBOLS = {
    LOWER_IS_BETTER: '≤',
    HIGHER_IS_BETTER: '≥',
}

/**
 * Symbol of a direction, `≤` when lower is better, `≥` when higher is; empty when unknown.
 */
export const directionSymbol = (direction) => DIRECTION_SYMBOLS[direction] ?? ''

/**
 * Symbol of the direction of a reading of the catalogue, by its key.
 */
export const readingDirectionSymbol = (key) => directionSymbol(readingOf(key)?.direction)

/**
 * The target of a reading with its direction, `≤ 1d`, `≥ 90%`; `null` with no target.
 */
export const targetText = (reading) => {
    if (!isNumber(reading.target)) return null
    const symbol = DIRECTION_SYMBOLS[reading.direction] ?? ''
    return `${symbol} ${formatReadingValue(reading.key, reading.target)}`.trim()
}

/**
 * Number of samples the value rests on, `null` when the reading has none to give.
 */
export const sampleCount = (reading) => {
    const count = reading.details?.count
    return isNumber(count) ? count : null
}

export const sampleCountText = (count) => {
    if (!isNumber(count)) return null
    return count === 1 ? '1 sample' : `${count} samples`
}

/**
 * The readings of a scorecard, one row per reading key, in the catalogue order (the keys out of the
 * catalogue last), each row giving the reading of each set by name.
 */
export const scorecardRows = (scorecard) => {
    const sets = scorecard?.sets ?? []
    const keys = [...new Set(sets.flatMap(set => set.readings.map(it => it.key)))]
    keys.sort((a, b) => readingRank(a) - readingRank(b) || a.localeCompare(b))
    return keys.map(key => ({
        key,
        readings: Object.fromEntries(
            sets
                .map(set => [set.name, set.readings.find(it => it.key === key)])
                .filter(([, reading]) => reading)
        ),
    }))
}

/**
 * Time of the latest computation among the readings of a scorecard, as the API gives it (a UTC
 * date-time), `null` if nothing has been computed.
 */
export const latestComputedAt = (scorecard) => {
    let latest = null
    ;(scorecard?.sets ?? [])
        .flatMap(set => set.readings)
        .forEach(({computedAt}) => {
            const time = Date.parse(computedAt)
            if (!isNaN(time) && (latest === null || time > latest.time)) {
                latest = {time, computedAt}
            }
        })
    return latest?.computedAt ?? null
}

/**
 * Whether a time of computation is later than another, `null` being earlier than anything.
 */
export const isComputedAfter = (computedAt, other) =>
    computedAt !== null && (other === null || Date.parse(computedAt) > Date.parse(other))

/**
 * Number of days of the window of a reading.
 */
export const windowDays = (reading) => {
    const start = Date.parse(reading.windowStart)
    const end = Date.parse(reading.windowEnd)
    if (isNaN(start) || isNaN(end)) return null
    return Math.round((end - start) / 86400000)
}

/**
 * The marker a reading was read up to, from its details.
 */
export const markerText = (details) => {
    const marker = details?.marker
    if (!marker) return 'No marker'
    if (details.markerKind === 'ENVIRONMENT') {
        return `Environment: ${marker.environment}${marker.qualifier ? ` [${marker.qualifier}]` : ''}`
    }
    const levels = Object.entries(marker.levels ?? {})
    if (levels.length === 0) return 'No marker'
    return `Promotion: ${levels.map(([branch, level]) => `${level} on ${branch}`).join(', ')}`
}

const SCOPE_KINDS = {
    BRANCH_MODEL: 'branch model',
    ALL_BRANCHES: 'all branches, no branch model',
}

/**
 * The branches which fed a reading, from its details, and which case gave them.
 */
export const scopeText = (details) => {
    const scope = details?.scope
    if (!scope) return '-'
    const branches = scope.branches?.length > 0 ? scope.branches.join(', ') : 'No branch'
    const kind = SCOPE_KINDS[scope.kind]
    return kind ? `${branches} (${kind})` : branches
}

const ofCount = (part, count) => `${part} of ${count}`

/**
 * The `details` of a reading which explain its value, beyond the marker and the scope, as a list
 * of `{key, label, text}`, or `{key, label, timestamp}` for a moment in time.
 */
export const readingDetailItems = (reading) => {
    const details = reading.details ?? {}
    const items = []
    const unit = readingOf(reading.key)?.unit
    const count = sampleCount(reading)
    if (count !== null) {
        items.push({key: 'count', label: 'Samples', text: String(count)})
    }
    // Statistics of a duration
    if (unit === DURATION) {
        const stats = [['p90', '90th percentile'], ['mean', 'Mean'], ['min', 'Min'], ['max', 'Max']]
        stats.forEach(([field, label]) => {
            if (isNumber(details[field])) {
                items.push({key: field, label, text: formatDuration(details[field])})
            }
        })
    }
    // Success rate up to a promotion
    if (isNumber(details.promoted)) {
        items.push({key: 'promoted', label: 'Promoted', text: ofCount(details.promoted, count)})
    }
    // Success rate up to an environment
    if (isNumber(details.done)) {
        items.push({key: 'done', label: 'Done', text: String(details.done)})
    }
    if (isNumber(details.failed)) {
        items.push({key: 'failed', label: 'Failed', text: String(details.failed)})
    }
    // Time to restore: the outages still going on
    if (isNumber(details.open)) {
        items.push({key: 'open', label: 'Open outages', text: String(details.open)})
        if (details.openSince) {
            items.push({key: 'openSince', label: 'Open since', timestamp: details.openSince})
        }
    }
    // Builds in flight, left out
    const inFlight = details.inFlight
    if (inFlight) {
        if (inFlight.excluded > 0) {
            const builds = inFlight.excluded === 1 ? '1 build left out' : `${inFlight.excluded} builds left out`
            items.push({
                key: 'inFlight',
                label: 'In flight',
                text: `${builds}, created within ${formatDuration(inFlight.leadTime)} (the median lead time) before the end of the window`,
            })
            items.push({key: 'inFlightSince', label: 'In flight since', timestamp: inFlight.since})
        } else {
            items.push({key: 'inFlight', label: 'In flight', text: 'No build left out'})
        }
    }
    // Test readings
    if (isNumber(details.passed)) {
        items.push({key: 'passed', label: 'Passed', text: ofCount(details.passed, count)})
    }
    if (isNumber(details.flaky)) {
        items.push({key: 'flaky', label: 'Flaky', text: ofCount(details.flaky, count)})
    }
    if (Array.isArray(details.testStamps)) {
        items.push({
            key: 'testStamps',
            label: 'Test stamps',
            text: details.testStamps.length > 0 ? details.testStamps.join(', ') : 'None',
        })
    }
    return items
}

/**
 * Points of the sparkline of a reading: its daily snapshots, oldest first, `null` for a day it was
 * unknown so that the line breaks there.
 */
export const sparklinePoints = (reading) =>
    (reading.history ?? []).map(({day, value}) => ({day, value: isNumber(value) ? value : null}))
