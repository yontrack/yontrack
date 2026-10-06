import {
    countSegments,
    countsLabel,
    countsText,
    DEFAULT_ESTATE_SORT,
    estatePageQuery,
    estatePageState,
    estateReadingKeys,
    estateRowCounts,
    estateRows,
    formatMedian,
    hasEstimatedReading,
    metText,
    rollUp,
    sortEstateRows,
    SUMMARY_SORT_KEY,
} from "@components/extension/scorecard/estates/estateViewModel";

const reading = (key, props = {}) => ({
    key,
    value: null,
    basis: 'MEASURED',
    unknownReason: null,
    target: null,
    targetMet: null,
    ...props,
})

const set = (id, name, readings) => ({project: {id, name}, readings})

const row = (name, readings) => ({
    project: {id: name, name},
    readings: Object.fromEntries(readings.map(it => [it.key, it])),
})

describe('estateReadingKeys', () => {
    it('gives every reading of the catalogue, in its order, even before any computation', () => {
        expect(estateReadingKeys([])).toEqual([
            'delivery.leadTime',
            'delivery.frequency',
            'delivery.successRate',
            'delivery.mttr',
            'quality.testPassRate',
            'quality.testFlakiness',
            'security.maturity',
            'security.remediationTime',
            'security.overdue',
        ])
    })

    it('gives the readings out of the catalogue last, by key', () => {
        const keys = estateReadingKeys([
            set(1, 'a', [reading('other.z'), reading('delivery.mttr'), reading('other.a')]),
        ])
        expect(keys.slice(-2)).toEqual(['other.a', 'other.z'])
        expect(keys).toHaveLength(11)
    })
})

describe('estateRows', () => {
    it('gives one row per project, its readings by key', () => {
        const leadTime = reading('delivery.leadTime', {value: 3600})
        const rows = estateRows([
            set(1, 'alpha', [leadTime]),
            set(2, 'beta', []),
        ])
        expect(rows).toEqual([
            {project: {id: 1, name: 'alpha'}, readings: {'delivery.leadTime': leadTime}},
            {project: {id: 2, name: 'beta'}, readings: {}},
        ])
    })

    it('leaves out the estimated readings when only the measured ones are asked for', () => {
        const measured = reading('delivery.leadTime', {value: 3600})
        const estimated = reading('delivery.frequency', {value: 2, basis: 'ESTIMATED'})
        const sets = [set(1, 'alpha', [measured, estimated])]
        expect(estateRows(sets).at(0).readings).toEqual({
            'delivery.leadTime': measured,
            'delivery.frequency': estimated,
        })
        expect(estateRows(sets, {measuredOnly: true}).at(0).readings).toEqual({
            'delivery.leadTime': measured,
        })
    })
})

describe('hasEstimatedReading', () => {
    it('is false while no reading is estimated, as in 6.x', () => {
        expect(hasEstimatedReading([])).toBe(false)
        expect(hasEstimatedReading([
            set(1, 'alpha', [
                reading('delivery.leadTime', {value: 3600}),
                reading('delivery.mttr', {basis: 'UNKNOWN', unknownReason: 'NO_FAILURE'}),
            ]),
            set(2, 'beta', []),
        ])).toBe(false)
    })

    it('is true once one reading of one project is estimated', () => {
        expect(hasEstimatedReading([
            set(1, 'alpha', [reading('delivery.leadTime', {value: 3600})]),
            set(2, 'beta', [reading('delivery.frequency', {value: 2, basis: 'ESTIMATED'})]),
        ])).toBe(true)
    })
})

describe('rollUp', () => {
    it('gives the median of the measured values, an odd count taking the middle one', () => {
        const rows = [
            row('a', [reading('delivery.leadTime', {value: 300})]),
            row('b', [reading('delivery.leadTime', {value: 100})]),
            row('c', [reading('delivery.leadTime', {value: 200})]),
        ]
        expect(rollUp(rows, 'delivery.leadTime')).toEqual({median: 200, measured: 3, unknown: 0, missed: 0, met: 0, neutral: 3})
    })

    it('gives the mean of the two middle values for an even count', () => {
        const rows = [
            row('a', [reading('delivery.frequency', {value: 1})]),
            row('b', [reading('delivery.frequency', {value: 4})]),
            row('c', [reading('delivery.frequency', {value: 2})]),
            row('d', [reading('delivery.frequency', {value: 10})]),
        ]
        expect(rollUp(rows, 'delivery.frequency').median).toBe(3)
    })

    it('counts the unknown readings apart, and leaves them out of the median', () => {
        const rows = [
            row('a', [reading('delivery.leadTime', {value: 100})]),
            row('b', [reading('delivery.leadTime', {basis: 'UNKNOWN', unknownReason: 'NO_SAMPLES'})]),
            row('c', [reading('delivery.leadTime', {basis: 'UNKNOWN', unknownReason: 'NO_MARKER'})]),
        ]
        expect(rollUp(rows, 'delivery.leadTime')).toEqual({median: 100, measured: 1, unknown: 2, missed: 0, met: 0, neutral: 1})
    })

    it('counts no failure in the window neither as unknown nor in the median', () => {
        const rows = [
            row('a', [reading('delivery.mttr', {value: 600})]),
            row('b', [reading('delivery.mttr', {basis: 'UNKNOWN', unknownReason: 'NO_FAILURE'})]),
        ]
        expect(rollUp(rows, 'delivery.mttr')).toEqual({median: 600, measured: 1, unknown: 0, missed: 0, met: 0, neutral: 2})
    })

    it('counts no target set neither as unknown nor in the median', () => {
        const rows = [
            row('a', [reading('security.overdue', {value: 3})]),
            row('b', [reading('security.overdue', {basis: 'UNKNOWN', unknownReason: 'NO_TARGET'})]),
            row('c', [reading('security.overdue', {basis: 'UNKNOWN', unknownReason: 'NO_TARGET'})]),
        ]
        expect(rollUp(rows, 'security.overdue')).toEqual({median: 3, measured: 1, unknown: 0, missed: 0, met: 0, neutral: 3})
    })

    it('counts the readings missing their target', () => {
        const rows = [
            row('a', [reading('delivery.successRate', {value: 80, target: 90, targetMet: false})]),
            row('b', [reading('delivery.successRate', {value: 95, target: 90, targetMet: true})]),
            row('c', [reading('delivery.successRate', {value: 50, target: 90, targetMet: false})]),
        ]
        expect(rollUp(rows, 'delivery.successRate')).toEqual({median: 80, measured: 3, unknown: 0, missed: 2, met: 1, neutral: 0})
    })

    it('has no median with no measured value, and ignores the projects with no reading yet', () => {
        const rows = [
            row('a', []),
            row('b', [reading('security.remediationTime', {basis: 'UNKNOWN', unknownReason: 'NO_SAMPLES'})]),
        ]
        expect(rollUp(rows, 'security.remediationTime')).toEqual({median: null, measured: 0, unknown: 1, missed: 0, met: 0, neutral: 0})
    })
})

describe('formatMedian', () => {
    it('formats the median in the unit of the reading', () => {
        expect(formatMedian('delivery.leadTime', 5400)).toBe('1h 30m')
        expect(formatMedian('delivery.successRate', 92.5)).toBe('92.5%')
    })

    it('keeps the half of a median of counts and of rungs', () => {
        expect(formatMedian('security.overdue', 2)).toBe('2')
        expect(formatMedian('security.overdue', 1.5)).toBe('1.5')
        expect(formatMedian('security.maturity', 2)).toBe('2 · Covered')
        expect(formatMedian('security.maturity', 1.5)).toBe('1.5')
    })

    it('gives a dash with no median', () => {
        expect(formatMedian('delivery.leadTime', null)).toBe('-')
    })
})

describe('sortEstateRows', () => {
    const rows = [
        row('gamma', [reading('delivery.leadTime', {value: 200})]),
        row('alpha', [reading('delivery.leadTime', {basis: 'UNKNOWN', unknownReason: 'NO_SAMPLES'})]),
        row('delta', [reading('delivery.leadTime', {value: 100})]),
        row('beta', []),
        row('epsilon', [reading('delivery.leadTime', {value: 300})]),
    ]
    const names = (sorted) => sorted.map(it => it.project.name)

    const missing = (count) => Array.from({length: count}, (_, index) =>
        reading(`other.${index}`, {value: 1, target: 0, targetMet: false}))
    const judged = [
        row('beta', missing(1)),
        row('delta', [...missing(2), reading('delivery.leadTime', {value: 1, target: 2, targetMet: true})]),
        row('alpha', missing(1)),
        row('gamma', [reading('delivery.leadTime', {basis: 'UNKNOWN', unknownReason: 'NO_SAMPLES'})]),
        row('epsilon', missing(2)),
    ]

    it('sorts the worst projects first by default: by missed readings, the most first, then by name', () => {
        expect(DEFAULT_ESTATE_SORT).toEqual({key: SUMMARY_SORT_KEY, order: 'descend'})
        expect(names(sortEstateRows(judged, {}))).toEqual(['delta', 'epsilon', 'alpha', 'beta', 'gamma'])
        expect(names(sortEstateRows(judged))).toEqual(['delta', 'epsilon', 'alpha', 'beta', 'gamma'])
        expect(names(sortEstateRows(judged, DEFAULT_ESTATE_SORT))).toEqual(['delta', 'epsilon', 'alpha', 'beta', 'gamma'])
    })

    it('sorts by missed readings the other way, the ties still by name', () => {
        expect(names(sortEstateRows(judged, {key: SUMMARY_SORT_KEY, order: 'ascend'})))
            .toEqual(['gamma', 'alpha', 'beta', 'delta', 'epsilon'])
    })

    it('sorts by project name, either way', () => {
        expect(names(sortEstateRows(rows, {key: 'project', order: 'ascend'})))
            .toEqual(['alpha', 'beta', 'delta', 'epsilon', 'gamma'])
        expect(names(sortEstateRows(rows, {key: 'project', order: 'descend'})))
            .toEqual(['gamma', 'epsilon', 'delta', 'beta', 'alpha'])
    })

    it('sorts by the value of a reading, the projects with no value last, by name, either way', () => {
        expect(names(sortEstateRows(rows, {key: 'delivery.leadTime', order: 'ascend'})))
            .toEqual(['delta', 'gamma', 'epsilon', 'alpha', 'beta'])
        expect(names(sortEstateRows(rows, {key: 'delivery.leadTime', order: 'descend'})))
            .toEqual(['epsilon', 'gamma', 'delta', 'alpha', 'beta'])
    })

    it('does not change the given rows', () => {
        const copy = [...rows]
        sortEstateRows(rows, {key: 'delivery.leadTime', order: 'ascend'})
        sortEstateRows(rows)
        expect(rows).toEqual(copy)
    })
})

describe('estateRowCounts', () => {
    it('counts the readings of a project by judgement, the judged ones being the met and the missed ones', () => {
        const counts = estateRowCounts(row('alpha', [
            reading('delivery.leadTime', {value: 3600, target: 86400, targetMet: true}),
            reading('delivery.frequency', {value: 1, target: 5, targetMet: false}),
            reading('delivery.successRate', {value: 90, target: 80, targetMet: true}),
            reading('quality.testPassRate', {value: 92}),
            reading('delivery.mttr', {basis: 'UNKNOWN', unknownReason: 'NO_FAILURE'}),
            reading('security.overdue', {basis: 'UNKNOWN', unknownReason: 'NO_TARGET'}),
            reading('security.remediationTime', {basis: 'UNKNOWN', unknownReason: 'NO_SAMPLES'}),
        ]))
        expect(counts).toEqual({missed: 1, met: 2, neutral: 3, unknown: 1, judged: 3})
    })

    it('counts nowhere a project whose readings are not computed yet', () => {
        expect(estateRowCounts(row('alpha', []))).toEqual({missed: 0, met: 0, neutral: 0, unknown: 0, judged: 0})
    })
})

describe('The counts of a row or of a roll-up in words', () => {
    it('says how many judged readings are met', () => {
        expect(metText({met: 2, judged: 3})).toBe('2 of 3 met')
        expect(metText({met: 0, judged: 1})).toBe('0 of 1 met')
        expect(metText({met: 0, judged: 0})).toBe('None judged')
    })

    it('spells out every count in the label of the bar', () => {
        expect(countsLabel({missed: 1, met: 2, neutral: 0, unknown: 1}))
            .toBe('1 missed, 2 met, 0 neutral, 1 unknown')
    })

    it('says the counts in one short line, leaving the zeros out', () => {
        expect(countsText({missed: 1, met: 2, neutral: 0, unknown: 1})).toBe('1 missed · 2 met · 1 unknown')
        expect(countsText({missed: 0, met: 0, neutral: 0, unknown: 0})).toBe('Not computed yet')
    })

    it('gives the segments of the bar, missed first, the empty ones left out', () => {
        expect(countSegments({missed: 1, met: 2, neutral: 0, unknown: 1})).toEqual([
            {judgement: 'missed', count: 1},
            {judgement: 'met', count: 2},
            {judgement: 'unknown', count: 1},
        ])
        expect(countSegments({missed: 0, met: 0, neutral: 0, unknown: 0})).toEqual([])
    })
})

describe('The state of the estate page in the query of its URL', () => {

    it('opens on the readings, with no finding, by default', () => {
        expect(estatePageState({name: 'Products'})).toEqual({tab: 'readings', finding: null, search: null})
        expect(estatePageState(undefined)).toEqual({tab: 'readings', finding: null, search: null})
    })

    it('reads the fan-out tab and the finding searched', () => {
        expect(estatePageState({name: 'Products', tab: 'fanout', finding: 'CVE-2024-38816'}))
            .toEqual({tab: 'fanout', finding: 'CVE-2024-38816', search: null})
    })

    it('trims the finding, and ignores a blank one', () => {
        expect(estatePageState({tab: 'fanout', finding: '  CVE-1 '}).finding).toBe('CVE-1')
        expect(estatePageState({tab: 'fanout', finding: '  '}).finding).toBeNull()
    })

    it('falls back on the readings for an unknown tab', () => {
        expect(estatePageState({tab: 'nope'}).tab).toBe('readings')
    })

    it('reads the first value of a repeated parameter', () => {
        expect(estatePageState({tab: ['fanout', 'readings'], finding: ['CVE-1', 'CVE-2']}))
            .toEqual({tab: 'fanout', finding: 'CVE-1', search: null})
    })

    it('reads the text searched, trimmed, and ignores a blank one', () => {
        expect(estatePageState({tab: 'fanout', search: '  cve-2024 '}))
            .toEqual({tab: 'fanout', finding: null, search: 'cve-2024'})
        expect(estatePageState({tab: 'fanout', search: '  '}).search).toBeNull()
    })

    it('reads a finding picked among the results of a search, with the search', () => {
        expect(estatePageState({tab: 'fanout', finding: 'CVE-2024-38816', search: 'cve-2024'}))
            .toEqual({tab: 'fanout', finding: 'CVE-2024-38816', search: 'cve-2024'})
    })

    it('writes the fan-out tab and the finding', () => {
        expect(estatePageQuery({tab: 'fanout', finding: 'CVE-2024-38816'}))
            .toEqual({tab: 'fanout', finding: 'CVE-2024-38816'})
        expect(estatePageQuery({tab: 'fanout', finding: null})).toEqual({tab: 'fanout'})
    })

    it('leaves the default tab out, and keeps the finding for a return to the fan-out', () => {
        expect(estatePageQuery({tab: 'readings', finding: null})).toEqual({})
        expect(estatePageQuery({tab: 'readings', finding: 'CVE-1'})).toEqual({finding: 'CVE-1'})
    })

    it('writes the text searched, trimmed, whatever the tab', () => {
        expect(estatePageQuery({tab: 'fanout', finding: null, search: ' cve-2024 '}))
            .toEqual({tab: 'fanout', search: 'cve-2024'})
        expect(estatePageQuery({tab: 'readings', search: 'cve'})).toEqual({search: 'cve'})
        expect(estatePageQuery({tab: 'fanout', search: '  '})).toEqual({tab: 'fanout'})
    })

    it('reads back what it writes', () => {
        const state = {tab: 'fanout', finding: 'CVE-2024-38816', search: null}
        expect(estatePageState(estatePageQuery(state))).toEqual(state)
        const searched = {tab: 'fanout', finding: 'CVE-2024-38816', search: 'cve-2024'}
        expect(estatePageState(estatePageQuery(searched))).toEqual(searched)
    })
})
