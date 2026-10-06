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
 * The judgements of the heatmap of the estate view, in the order of its bars, the worst first:
 *
 * - `missed` / `met` - judged against the target of the estate
 * - `neutral` - not judged, nothing went wrong: a value with no target, a time to restore with no
 *   failure in the window, overdue findings with no target set
 * - `unknown` - Yontrack cannot tell
 *
 * A reading not computed yet has none.
 */
const ESTATE_JUDGEMENTS = ['missed', 'met', 'neutral', 'unknown']

/**
 * The judgement of a reading in the heatmap of the estate view - one of `ESTATE_JUDGEMENTS` -
 * `null` for a reading not computed yet.
 */
export const estateJudgement = (reading) => {
    if (!reading) return null
    switch (readingJudgement(reading)) {
        case 'MISSED':
            return 'missed'
        case 'MET':
            return 'met'
        case 'UNKNOWN':
            return 'unknown'
        default:
            return 'neutral'
    }
}

const countJudgements = (readings) => {
    const counts = Object.fromEntries(ESTATE_JUDGEMENTS.map(it => [it, 0]))
    readings.forEach(reading => {
        const judgement = estateJudgement(reading)
        if (judgement) counts[judgement]++
    })
    return counts
}

/**
 * The readings of one row by judgement - `missed`, `met`, `neutral`, `unknown` - and `judged`,
 * the met and missed ones only. The readings not computed yet are counted nowhere.
 */
export const estateRowCounts = (row) => {
    const counts = countJudgements(Object.values(row.readings))
    return {...counts, judged: counts.missed + counts.met}
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
 * - `met` - number of projects whose reading meets the target of the estate
 * - `neutral` - number of projects whose reading is not judged, nothing having gone wrong: a value
 *   with no target, no failure in the window, no target set
 */
export const rollUp = (rows, key) => {
    const readings = rows.map(row => row.readings[key]).filter(it => it)
    const values = readings.filter(hasValue).map(it => it.value)
    return {
        median: median(values),
        measured: values.length,
        ...countJudgements(readings),
    }
}

/**
 * The segments of the stacked bar of some counts by judgement, in the order of `ESTATE_JUDGEMENTS`,
 * each with its `count`; the empty ones left out.
 */
export const countSegments = (counts) => ESTATE_JUDGEMENTS
    .filter(it => counts[it] > 0)
    .map(it => ({judgement: it, count: counts[it]}))

/**
 * One count of a judgement in words: "2 met".
 */
export const countWords = (judgement, count) => `${count} ${judgement}`

/**
 * Every count by judgement, for the label of a stacked bar: "1 missed, 2 met, 0 neutral, 1 unknown".
 */
export const countsLabel = (counts) => ESTATE_JUDGEMENTS.map(it => countWords(it, counts[it])).join(', ')

/**
 * The counts by judgement in one short line, the zeros left out: "1 missed · 2 met · 1 unknown";
 * "Not computed yet" with none.
 */
export const countsText = (counts) => {
    const parts = countSegments(counts).map(({judgement, count}) => countWords(judgement, count))
    return parts.length > 0 ? parts.join(' · ') : 'Not computed yet'
}

/**
 * How many of the judged readings of a row are met: "2 of 3 met"; "None judged" with none.
 */
export const metText = ({met, judged}) => judged > 0 ? `${met} of ${judged} met` : 'None judged'

/**
 * What the roll-up row says, in words — as `rollUp` counts.
 */
export const ESTATE_ROLLUP_TEXT = 'Median over the projects with a value. Missed counts the projects missing the target of this estate, and met the ones meeting it. Neutral counts the ones not judged, which are not unknown: a value with no target, No failure, No target set. Unknown counts the projects whose reading is unknown. A project not computed yet is counted nowhere.'

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

/**
 * The key of the sort by the number of missed readings of a project, its summary.
 */
export const SUMMARY_SORT_KEY = 'summary'

/**
 * The sort of the estate view until the user picks another: the worst projects first, the ones
 * missing the most readings, then by name.
 */
export const DEFAULT_ESTATE_SORT = {key: SUMMARY_SORT_KEY, order: 'descend'}

const byName = (a, b) => a.project.name.localeCompare(b.project.name)

/**
 * The rows sorted, as a new list:
 *
 * - by the number of missed readings, then by name, when `key` is `summary` or not given - the
 *   worst projects first when not given
 * - by project name when `key` is `project`
 * - by the value of the reading `key` otherwise, the projects with no value - unknown, neutral,
 *   not computed - last whatever the order, by name
 *
 * @param order `ascend` (default) or `descend`
 */
export const sortEstateRows = (rows, sort = {}) => {
    const {key, order} = sort.key ? sort : DEFAULT_ESTATE_SORT
    const direction = order === 'descend' ? -1 : 1
    if (key === PROJECT_SORT_KEY) {
        return [...rows].sort((a, b) => direction * byName(a, b))
    }
    if (key === SUMMARY_SORT_KEY) {
        const missed = new Map(rows.map(row => [row, estateRowCounts(row).missed]))
        return [...rows].sort((a, b) => direction * (missed.get(a) - missed.get(b)) || byName(a, b))
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
 * The state of the estate page from the query of its URL — `?tab=fanout&finding=CVE-2024-38816`, or
 * `?tab=fanout&search=cve-2024` — so that a fan-out or a search can be shared by its link: its tab,
 * the readings by default, the external ID of the finding whose fan-out is shown, and the text
 * searched among the external IDs — each trimmed, `null` when there is none.
 *
 * A finding picked among the results of a search keeps the search, to go back to its results.
 */
export function estatePageState(query = {}) {
    const tab = single(query?.tab)
    return {
        tab: ESTATE_TABS.includes(tab) ? tab : ESTATE_TAB_READINGS,
        finding: fanOutExternalId(single(query?.finding)),
        search: fanOutExternalId(single(query?.search)),
    }
}

/**
 * The query of the URL from the state of the estate page: the tab when it is not the default one,
 * and the finding and the text searched, whatever the tab, so that going back to the fan-out finds
 * them again.
 */
export function estatePageQuery({tab, finding, search} = {}) {
    const query = {}
    if (tab && tab !== ESTATE_TAB_READINGS) {
        query.tab = tab
    }
    const externalId = fanOutExternalId(finding)
    if (externalId) {
        query.finding = externalId
    }
    const text = fanOutExternalId(search)
    if (text) {
        query.search = text
    }
    return query
}
