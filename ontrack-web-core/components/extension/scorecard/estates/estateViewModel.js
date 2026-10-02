/**
 * What the estate view makes of `Estate.projectSets`: the set of the estate of each of its projects,
 * shown as projects × readings, with a roll-up row and a sort. Pure functions: the judgement of a
 * reading, its unit and its wording come from the scorecard model.
 */

import {
    COUNT,
    formatReadingValue,
    READINGS,
    readingJudgement,
    readingRank,
    readingUnit,
    RUNG,
} from "@components/extension/scorecard/scorecardModel";
import {fanOutExternalId} from "@components/extension/scorecard/estates/estateFanOutModel";

/**
 * Judgements of a reading which carry a measured value.
 */
const MEASURED_JUDGEMENTS = ['MET', 'MISSED', 'SHOWN']

/**
 * Whether a reading has a value to show, measured or estimated, judged or not - neither unknown
 * nor neutral (a time to restore with no failure in the window, overdue findings with no target set).
 */
export const hasValue = (reading) => !!reading && MEASURED_JUDGEMENTS.includes(readingJudgement(reading))

/**
 * The readings shown as columns: every reading of the catalogue, in its order, whether computed or
 * not, then the readings out of it found in the sets, by key.
 */
export const estateReadingKeys = (projectSets) => {
    const keys = new Set(READINGS.map(it => it.key))
    ;(projectSets ?? []).forEach(set => set.readings.forEach(it => keys.add(it.key)))
    return [...keys].sort((a, b) => readingRank(a) - readingRank(b) || a.localeCompare(b))
}

/**
 * Whether some project of the estate holds an `ESTIMATED` reading - never in 6.x. The toggle
 * showing the measured readings only appears only then.
 */
export const hasEstimatedReading = (projectSets) =>
    (projectSets ?? []).some(set => set.readings.some(it => it.basis === 'ESTIMATED'))

/**
 * One row per project of the estate, in the order of the sets, each with its readings by key -
 * without the estimated ones when `measuredOnly`.
 */
export const estateRows = (projectSets, {measuredOnly = false} = {}) =>
    (projectSets ?? []).map(set => ({
        project: set.project,
        readings: Object.fromEntries(
            set.readings
                .filter(it => !measuredOnly || it.basis !== 'ESTIMATED')
                .map(it => [it.key, it])
        ),
    }))

const median = (values) => {
    if (values.length === 0) return null
    const sorted = [...values].sort((a, b) => a - b)
    const middle = Math.floor(sorted.length / 2)
    return sorted.length % 2 === 1 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2
}

/**
 * The roll-up of one reading over the rows of an estate:
 *
 * - `median` - of the values of the projects which have one, `null` when none has
 * - `measured` - number of projects with a value
 * - `unknown` - number of projects whose reading is unknown; a neutral reading - a time to restore
 *   with no failure in the window, overdue findings with no target set - is not unknown, and a
 *   project whose readings are not computed yet is not counted
 * - `missed` - number of projects whose reading misses the target of the estate
 */
export const rollUp = (rows, key) => {
    const readings = rows.map(row => row.readings[key]).filter(it => it)
    const values = readings.filter(hasValue).map(it => it.value)
    return {
        median: median(values),
        measured: values.length,
        unknown: readings.filter(it => readingJudgement(it) === 'UNKNOWN').length,
        missed: readings.filter(it => readingJudgement(it) === 'MISSED').length,
    }
}

/**
 * What the roll-up row says, in words — as `rollUp` counts.
 */
export const ESTATE_ROLLUP_TEXT = 'Median over the projects with a value. Unknown counts the projects whose reading is unknown: No failure and No target set are not unknown, and a project not computed yet is not counted. Missed counts the projects missing the target of this estate.'

/**
 * A median in the unit of its reading. The median of counts or of rungs may fall between two of
 * them, and keeps its half rather than being rounded to a count or named after a rung.
 */
export const formatMedian = (key, value) => {
    if (typeof value !== 'number' || isNaN(value)) return '-'
    const unit = readingUnit(key)
    if ((unit === COUNT || unit === RUNG) && !Number.isInteger(value)) {
        return String(Number(value.toFixed(1)))
    }
    return formatReadingValue(key, value)
}

/**
 * The key of the sort by project name.
 */
export const PROJECT_SORT_KEY = 'project'

const byName = (a, b) => a.project.name.localeCompare(b.project.name)

/**
 * The rows sorted, as a new list:
 *
 * - by project name when `key` is `project` or not given
 * - by the value of the reading `key` otherwise, the projects with no value - unknown, neutral,
 *   not computed - last whatever the order, by name
 *
 * @param order `ascend` (default) or `descend`
 */
export const sortEstateRows = (rows, {key, order} = {}) => {
    const direction = order === 'descend' ? -1 : 1
    if (!key || key === PROJECT_SORT_KEY) {
        return [...rows].sort((a, b) => direction * byName(a, b))
    }
    const valueOf = (row) => hasValue(row.readings[key]) ? row.readings[key].value : null
    return [...rows].sort((a, b) => {
        const va = valueOf(a)
        const vb = valueOf(b)
        if (va === null && vb === null) return byName(a, b)
        if (va === null) return 1
        if (vb === null) return -1
        return direction * (va - vb) || byName(a, b)
    })
}

/**
 * Tabs of the estate page: its readings, the default one, and the findings fan-out.
 */
export const ESTATE_TAB_READINGS = 'readings'
export const ESTATE_TAB_FANOUT = 'fanout'
const ESTATE_TABS = [ESTATE_TAB_READINGS, ESTATE_TAB_FANOUT]

const single = (value) => Array.isArray(value) ? value[0] : value

/**
 * The state of the estate page from the query of its URL — `?tab=fanout&finding=CVE-2024-38816` —
 * so that a fan-out can be shared by its link: its tab, the readings by default, and the external ID
 * of the finding searched, trimmed, `null` when there is none.
 */
export function estatePageState(query = {}) {
    const tab = single(query?.tab)
    const finding = single(query?.finding)
    return {
        tab: ESTATE_TABS.includes(tab) ? tab : ESTATE_TAB_READINGS,
        finding: fanOutExternalId(finding),
    }
}

/**
 * The query of the URL from the state of the estate page: the tab when it is not the default one,
 * and the finding searched, whatever the tab, so that going back to the fan-out finds it again.
 */
export function estatePageQuery({tab, finding} = {}) {
    const query = {}
    if (tab && tab !== ESTATE_TAB_READINGS) {
        query.tab = tab
    }
    const externalId = fanOutExternalId(finding)
    if (externalId) {
        query.finding = externalId
    }
    return query
}
