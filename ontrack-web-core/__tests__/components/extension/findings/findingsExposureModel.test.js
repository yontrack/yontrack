import {
    earlierPeriodsSummary,
    exposureTimeline,
    formatExposureDuration,
} from "@components/extension/findings/findingsModel"

describe('formatExposureDuration', () => {

    it('counts in minutes under an hour, at least one', () => {
        expect(formatExposureDuration(0)).toBe('1 min')
        expect(formatExposureDuration(35 * 60 + 10)).toBe('35 min')
        expect(formatExposureDuration(3599)).toBe('59 min')
    })

    it('counts in hours under 48 hours', () => {
        expect(formatExposureDuration(3600)).toBe('1 h')
        expect(formatExposureDuration(31 * 3600 + 59 * 60)).toBe('31 h')
        expect(formatExposureDuration(48 * 3600 - 1)).toBe('47 h')
    })

    it('counts in whole days from 48 hours', () => {
        expect(formatExposureDuration(48 * 3600)).toBe('2 days')
        expect(formatExposureDuration(4 * 86400 + 4 * 3600)).toBe('4 days')
    })

    it('says when the exposure is ongoing', () => {
        expect(formatExposureDuration(6 * 86400, true)).toBe('6 days, ongoing')
    })

    it('says nothing without a duration', () => {
        expect(formatExposureDuration(null)).toBe('')
        expect(formatExposureDuration(undefined)).toBe('')
    })
})

describe('earlierPeriodsSummary', () => {

    const period = (durationSeconds, startedInBuild, endedInBuild, ongoing = false) =>
        ({durationSeconds, startedInBuild, endedInBuild, ongoing})

    it('is empty for a single period', () => {
        expect(earlierPeriodsSummary([period(3600, '1', null, true)])).toBe('')
        expect(earlierPeriodsSummary([])).toBe('')
        expect(earlierPeriodsSummary(undefined)).toBe('')
    })

    it('summarises the periods before the current one', () => {
        expect(earlierPeriodsSummary([
            period(19 * 3600, '2.5.0', '2.5.1'),
            period(3 * 86400, '2.5.3', null, true),
        ])).toBe('earlier: 19 h, 2.5.0 → 2.5.1')
    })

    it('says when a build is unknown', () => {
        expect(earlierPeriodsSummary([
            period(3 * 86400, null, null),
            period(2 * 86400, '2', '3'),
            period(3600, '4', null, true),
        ])).toBe('earlier: 3 days, build unknown → build unknown; 2 days, 2 → 3')
    })
})

describe('exposureTimeline', () => {

    const main = {id: 10, name: 'main'}
    const release = {id: 11, name: 'release-2.4'}
    const image = {id: 20, name: 'SECURITY.IMAGE'}
    const code = {id: 21, name: 'SECURITY.CODE'}
    // Axis from 2 Oct 00:00 to now, 12 Oct 00:00: 10 days, 10 % a day
    const now = new Date('2026-10-12T00:00:00Z')
    const day = (d, h = 0) => `2026-10-${String(d).padStart(2, '0')}T${String(h).padStart(2, '0')}:00:00`

    it('is null without any period', () => {
        expect(exposureTimeline([], now)).toBeNull()
        expect(exposureTimeline([{branch: main, validationStamp: image, periods: []}], now)).toBeNull()
    })

    it('places the periods and their accepted stretches on an axis from the earliest start to now', () => {
        const timeline = exposureTimeline([
            {
                branch: main, validationStamp: image, periods: [
                    {startedAt: day(2), endedAt: day(6), ongoing: false, acceptedSpans: []},
                ],
            },
            {
                branch: release, validationStamp: image, periods: [
                    {
                        startedAt: day(3), endedAt: null, ongoing: true, acceptedSpans: [
                            {from: day(4), to: null},
                        ],
                    },
                ],
            },
        ], now)
        expect(timeline.start.toISOString()).toBe('2026-10-02T00:00:00.000Z')
        expect(timeline.end.toISOString()).toBe('2026-10-12T00:00:00.000Z')
        expect(timeline.lanes.map(it => it.key)).toEqual(['10-20', '11-20'])
        const [onMain, onRelease] = timeline.lanes
        expect(onMain.showStamp).toBe(false)
        const fixed = onMain.bars[0]
        expect(fixed.left).toBeCloseTo(0)
        expect(fixed.width).toBeCloseTo(40)
        expect(fixed.fixed).toBe(true)
        const open = onRelease.bars[0]
        expect(open.left).toBeCloseTo(10)
        expect(open.width).toBeCloseTo(90)
        expect(open.fixed).toBe(false)
        expect(open.accepted).toHaveLength(1)
        expect(open.accepted[0].left).toBeCloseTo(20)
        expect(open.accepted[0].width).toBeCloseTo(80)
    })

    it('names the stamp of a lane only when its branch has several', () => {
        const period = {startedAt: day(2), endedAt: null, ongoing: true, acceptedSpans: []}
        const timeline = exposureTimeline([
            {branch: main, validationStamp: code, periods: [period]},
            {branch: main, validationStamp: image, periods: [period]},
            {branch: release, validationStamp: image, periods: [period]},
        ], now)
        expect(timeline.lanes.map(it => it.showStamp)).toEqual([true, true, false])
    })

    it('gives the axis ticks from its start to its end', () => {
        const timeline = exposureTimeline([
            {
                branch: main, validationStamp: image, periods: [
                    {startedAt: day(2), endedAt: null, ongoing: true, acceptedSpans: []},
                ],
            },
        ], now)
        expect(timeline.ticks[0].left).toBeCloseTo(0)
        expect(timeline.ticks[timeline.ticks.length - 1].left).toBeCloseTo(100)
        expect(timeline.ticks.length).toBeGreaterThanOrEqual(2)
    })
})
